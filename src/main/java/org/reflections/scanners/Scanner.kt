package org.reflections.scanners

import javassist.ClassPool
import javassist.CtBehavior
import javassist.CtMember
import javassist.LoaderClassPath
import javassist.bytecode.LocalVariableAttribute
import javassist.expr.ConstructorCall
import javassist.expr.ExprEditor
import javassist.expr.FieldAccess
import javassist.expr.MethodCall
import javassist.expr.NewExpr
import org.reflections.Configuration
import org.reflections.Filter
import org.reflections.adapters.ClassAdapter
import org.reflections.adapters.CreateClassAdapter
import org.reflections.adapters.JavassistMethodAdapter
import org.reflections.util.IndexKey
import org.reflections.util.Multimap
import org.reflections.util.classLoaders
import org.reflections.util.tryOrThrow
import org.reflections.vfs.VfsFile
import java.lang.annotation.Inherited
import java.lang.reflect.Modifier

abstract class Scanner {
    lateinit var configuration: Configuration
    lateinit var store: Multimap<IndexKey, IndexKey>
    var acceptResult: (fqn: IndexKey) -> Boolean = { true }

    abstract fun acceptsInput(file: String): Boolean
    abstract fun scan(vfsFile: VfsFile, classObject: ClassAdapter?): ClassAdapter?

    override fun equals(o: Any?) = this === o || o != null && javaClass == o.javaClass
    override fun hashCode() = javaClass.hashCode()
}

/**
 * collects all resources that are not classes in a collection
 *
 * key: value - {web.xml: WEB-INF/web.xml}
 */
class ResourcesScanner : Scanner() {
    override fun acceptsInput(file: String) = !file.endsWith(".class") //not a class

    override fun scan(vfsFile: VfsFile, classObject: ClassAdapter?): ClassAdapter? {
        store.put(IndexKey(vfsFile.name), IndexKey(vfsFile.relativePath!!))
        return classObject
    }
}

abstract class BaseClassScanner : Scanner() {
    override fun acceptsInput(file: String) = file.endsWith(".class")

    override fun scan(vfsFile: VfsFile, classObject: ClassAdapter?) =
            (classObject ?: tryOrThrow("could not create class object from vfsFile ${vfsFile.relativePath!!}") {
                CreateClassAdapter(vfsFile)
            }).also { scan(it) }

    abstract fun scan(cls: ClassAdapter)
}

class FieldAnnotationsScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) = cls.fields.forEach { field ->
        field.annotations.filter { annotation -> acceptResult(IndexKey(annotation)) }.forEach { annotation ->
            store.put(IndexKey(annotation), IndexKey("${cls.name}.${field.name}"))
        }
    }
}

/**
 * scans methods/constructors/fields usage.
 *
 * depends on [org.reflections.adapters.CreateJavassistClassAdapter] configured
 */
class MemberUsageScanner : BaseClassScanner() {
    private val classPool by lazy {
        ClassPool().also { pool ->
            classLoaders(configuration.classLoaders)
                .forEach { classLoader -> pool.appendClassPath(LoaderClassPath(classLoader)) }
        }
    }

    override fun scan(cls: ClassAdapter) = tryOrThrow("Could not scan method usage for ${cls.name}") {
        classPool.get(cls.name).run {
            declaredConstructors.forEach(::scanMember)
            declaredMethods.forEach(::scanMember)
            detach()
        }
    }

    private fun scanMember(member: CtBehavior) {
        //key contains this$/val$ means local field/parameter closure
        val key =
                "${member.declaringClassName}.${member.methodInfo.name}(${member.parameterNames})" //+ " #" + member.getMethodInfo().getLineNumber(0)
        member.instrument(object : ExprEditor() {
            override fun edit(e: NewExpr?) = tryOrThrow("Could not find new instance usage in $key") {
                put("${e!!.constructor.declaringClassName}.<init>(${e.constructor.parameterNames})", e.lineNumber, key)
            }

            override fun edit(m: MethodCall?) = tryOrThrow("Could not find member ${m!!.className} in $key") {
                put("${m.method.declaringClassName}.${m.methodName}(${m.method.parameterNames})", m.lineNumber, key)
            }

            override fun edit(c: ConstructorCall?) = tryOrThrow("Could not find member ${c!!.className} in $key") {
                put("${c.constructor.declaringClassName}.<init>(${c.constructor.parameterNames})", c.lineNumber, key)
            }

            override fun edit(f: FieldAccess?) = tryOrThrow("Could not find member ${f!!.fieldName} in $key") {
                put("${f.field.declaringClassName}.${f.fieldName}", f.lineNumber, key)
            }
        })
    }

    private fun put(key: String, lineNumber: Int, value: String) {
        if (acceptResult(IndexKey(key))) {
            store.put(IndexKey(key), IndexKey("$value #$lineNumber"))
        }
    }

    val CtMember.declaringClassName get() = declaringClass.name
    val CtBehavior.parameterNames get() = JavassistMethodAdapter(methodInfo).parameters.joinToString()
}

class MethodAnnotationsScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) = cls.methods.forEach { method ->
        method.annotations.filter { annotation -> acceptResult(IndexKey(annotation)) }
            .forEach { annotation -> store.put(IndexKey(annotation), method.getMethodFullKey(cls)) }
    }
}

class MethodParameterNamesScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) = cls.methods.forEach { method ->
        val key = method.getMethodFullKey(cls)
        if (acceptResult(key)) {
            val table =
                    (method as JavassistMethodAdapter).method.codeAttribute?.getAttribute(LocalVariableAttribute.tag) as LocalVariableAttribute?
            val length = table?.tableLength() ?: 0
            var i = if (Modifier.isStatic(method.method.accessFlags)) 0 else 1 //skip this
            if (i < length) {
                val names = mutableListOf<String>()
                while (i < length) {
                    names.add(method.method.constPool.getUtf8Info(table!!.nameIndex(i++)))
                }
                store.put(key, IndexKey(names.joinToString()))
            }
        }
    }
}

/**
 * scans methods/constructors and indexes parameters, return type and parameter annotations
 */
class MethodParameterScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) = cls.methods.forEach { method ->
        val signature = IndexKey(method.parameters.joinToString())
        if (acceptResult(signature)) {
            store.put(signature, method.getMethodFullKey(cls))
        }

        val returnTypeName = IndexKey(method.returnType)
        if (acceptResult(returnTypeName)) {
            store.put(returnTypeName, method.getMethodFullKey(cls))
        }

        method.parameters.indices.flatMap { method.parameterAnnotations(it) }.filter {
            acceptResult(IndexKey(it))
        }.forEach { store.put(IndexKey(it), method.getMethodFullKey(cls)) }
    }
}

class TypeElementsScanner(private var includeFields: Boolean = true,
                          private var includeMethods: Boolean = true,
                          private var includeAnnotations: Boolean = true,
                          var publicOnly: Boolean = true) : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) {
        if (acceptResult(IndexKey(cls.name))) {
            store.put(IndexKey(cls.name), IndexKey(""))

            if (includeFields) {
                cls.fields.map { it.name }.forEach {
                    store.put(IndexKey(cls.name), IndexKey(it))
                }
            }

            if (includeMethods) {
                cls.methods.filter { method -> !publicOnly || method.isPublic }.forEach { method ->
                    store.put(IndexKey(cls.name), IndexKey("${method.name}(${method.parameters.joinToString()})"))
                }
            }

            if (includeAnnotations) {
                cls.annotations.forEach { annotation ->
                    store.put(IndexKey(cls.name), IndexKey("@$annotation"))
                }
            }
        }
    }
}

/**
 * scans for class's annotations, where @Retention(RetentionPolicy.RUNTIME)
 */
class TypeAnnotationsScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) =
            cls.annotations.filter { acceptResult(IndexKey(it)) || it == Inherited::class.java.name }.forEach {
                //as an exception, accept Inherited as well
                store.put(IndexKey(it), IndexKey(cls.name))
            }
}

class SubTypesScanner(excludeObjectClass: Boolean = true) : BaseClassScanner() {
    init {
        if (excludeObjectClass) {
            //exclude direct Object subtypes
            val exclude = Filter.Exclude(Any::class.java.name)
            acceptResult = { exclude.test(it.value) }
        }
    }

    override fun scan(cls: ClassAdapter) {
        if (acceptResult(IndexKey(cls.superclass))) {
            store.put(IndexKey(cls.superclass), IndexKey(cls.name))
        }
        cls.interfaces.filter { acceptResult(IndexKey(it)) }.forEach {
            store.put(IndexKey(it), IndexKey(cls.name))
        }
    }
}