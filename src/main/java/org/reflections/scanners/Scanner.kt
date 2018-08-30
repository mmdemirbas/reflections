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
import org.reflections.Filter
import org.reflections.adapters.ClassAdapter
import org.reflections.adapters.CreateClassAdapter
import org.reflections.adapters.JavassistMethodAdapter
import org.reflections.util.Datum
import org.reflections.util.Multimap
import org.reflections.util.annotationType
import org.reflections.util.classForName
import org.reflections.util.classHieararchy
import org.reflections.util.defaultClassLoaders
import org.reflections.util.directParentsExceptObject
import org.reflections.util.fullName
import org.reflections.util.logDebug
import org.reflections.util.tryOrNull
import org.reflections.util.tryOrThrow
import org.reflections.util.withAnnotation
import org.reflections.util.withAnyParameterAnnotation
import org.reflections.vfs.VfsFile
import java.lang.annotation.Inherited
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.regex.Pattern

abstract class Scanner() {
    val store = Multimap<Datum, Datum>()

    fun keys() = store.keys()
    fun entries(): MutableSet<MutableMap.MutableEntry<Datum, MutableSet<Datum>>> = store.map.entries

    fun keyCount(): Int = store.keyCount()
    fun valueCount(): Int = store.valueCount()

    abstract fun acceptsInput(file: String): Boolean
    open fun acceptResult(fqn: Datum) = true

    // todo: eliminate classObject parameter and cache it elsewhere if needed.
    abstract fun scan(vfsFile: VfsFile, classObject: ClassAdapter?): ClassAdapter?

    override fun equals(o: Any?) = this === o || o != null && javaClass == o.javaClass
    override fun hashCode() = javaClass.hashCode()
    open fun afterScan() = Unit


    fun recursiveValuesIncludingSelf(keys: Collection<Datum>) = keys + recursiveValuesExcludingSelf(keys)

    fun recursiveValuesExcludingSelf(keys: Collection<Datum>): List<Datum> {
        val result = mutableListOf<Datum>()
        var values = values(keys)
        while (values.isNotEmpty()) {
            result += values
            values = values(values)
        }
        return result
    }

    fun values(keys: Collection<Datum>): Collection<Datum> = keys.flatMap(::values)
    fun values(): Collection<Datum> = store.values()
    fun values(key: Datum): Collection<Datum> = store.get(key).orEmpty()
}


/**
 * collects all resources that are not classes in a collection
 *
 * key: value - {web.xml: WEB-INF/web.xml}
 */
class ResourcesScanner : Scanner() {
    override fun acceptsInput(file: String) = !file.endsWith(".class") //not a class

    override fun scan(vfsFile: VfsFile, classObject: ClassAdapter?): ClassAdapter? {
        store.put(Datum(vfsFile.name), Datum(vfsFile.relativePath!!))
        return classObject
    }

    /**
     * get resources relative paths where simple name (key) matches given regular expression
     *
     *  ```Set<String> xmls = resources(".*\\.xml");```
     */
    fun resources(pattern: Pattern) = resources { pattern.matcher(it).matches() }

    /**
     * get resources relative paths where simple name (key) matches given namePredicate
     */
    fun resources(namePredicate: (String) -> Boolean) = values(store.keys().filter { namePredicate(it.value) })
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
        field.annotations.filter { annotation -> acceptResult(Datum(annotation)) }.forEach { annotation ->
            store.put(Datum(annotation), Datum("${cls.name}.${field.name}"))
        }
    }

    fun fieldsAnnotatedWith(annotation: Annotation) = fieldsAnnotatedWith(annotation.annotationType()).filter {
        withAnnotation(it, annotation)
    }

    fun fieldsAnnotatedWith(annotation: Class<out Annotation>) = values(annotation.fullName()).map {
        val field = it.value
        val className = field.substringBeforeLast('.')
        val fieldName = field.substringAfterLast('.')
        tryOrThrow("Can't resolve field named $fieldName") {
            classForName(className)!!.getDeclaredField(fieldName)
        }
    }
}

/**
 * scans methods/constructors/fields usage.
 *
 * depends on [org.reflections.adapters.CreateJavassistClassAdapter] configured
 */
class MemberUsageScanner(val classLoaders: List<ClassLoader> = defaultClassLoaders()) : BaseClassScanner() {
    private val classPool by lazy {
        ClassPool().also { pool ->
            classLoaders.forEach { classLoader ->
                pool.appendClassPath(LoaderClassPath(classLoader))
            }
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
        if (acceptResult(Datum(key))) {
            store.put(Datum(key), Datum("$value #$lineNumber"))
        }
    }

    val CtMember.declaringClassName get() = declaringClass.name
    val CtBehavior.parameterNames get() = JavassistMethodAdapter(methodInfo).parameters.joinToString()

    fun usages(o: AccessibleObject) = values(listOf(o.fullName())).map(::descriptorToMember)
}

class MethodAnnotationsScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) = cls.methods.forEach { method ->
        method.annotations.filter { annotation -> acceptResult(Datum(annotation)) }
            .forEach { annotation -> store.put(Datum(annotation), method.getMethodFullKey(cls)) }
    }

    fun constructorsAnnotatedWith(annotation: Annotation) =
            constructorsAnnotatedWith(annotation.annotationType()).filter {
                withAnnotation(it, annotation)
            }

    fun constructorsAnnotatedWith(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodAnnotations<Constructor<*>>()

    fun methodsAnnotatedWith(annotation: Annotation) =
            listOf(annotation.annotationClass.java.fullName()).methodAnnotations<Method>().filter {
                withAnnotation(it, annotation)
            }

    fun methodsAnnotatedWith(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodAnnotations<Method>()

    private inline fun <reified T> List<Datum>.methodAnnotations() =
            values(this).map { value -> descriptorToMember(value) }.filterIsInstance<T>()
}

class MethodParameterNamesScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) = cls.methods.forEach { method ->
        val key = method.getMethodFullKey(cls)
        if (acceptResult(key)) {
            val table =
                    (method as JavassistMethodAdapter).method.codeAttribute?.getAttribute(LocalVariableAttribute.tag) as LocalVariableAttribute?
            val length = table?.tableLength() ?: 0
            val startIndex = if (Modifier.isStatic(method.method.accessFlags)) 0 else 1 //skip this
            if (startIndex < length) {
                store.put(key, Datum((startIndex until length).joinToString {
                    method.method.constPool.getUtf8Info(table!!.nameIndex(it))
                }))
            }
        }
    }

    fun paramNames(executable: Executable): List<String> {
        val names = values(executable.fullName())
        return when {
            names.isEmpty() -> emptyList()
            else            -> names.single().value.split(", ").dropLastWhile { it.isEmpty() }
        }
    }
}

/**
 * scans methods/constructors and indexes parameters, return type and parameter annotations
 */
class MethodParameterScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) = cls.methods.forEach { method ->
        val signature = Datum(method.parameters.toString())
        if (acceptResult(signature)) {
            store.put(signature, method.getMethodFullKey(cls))
        }

        val returnTypeName = Datum(method.returnType)
        if (acceptResult(returnTypeName)) {
            store.put(returnTypeName, method.getMethodFullKey(cls))
        }

        method.parameters.indices.flatMap { method.parameterAnnotations(it) }.filter {
            acceptResult(Datum(it))
        }.forEach { store.put(Datum(it), method.getMethodFullKey(cls)) }
    }

    fun methodsWithParamTypes(vararg types: Class<*>) = types.map { it.fullName() }.asMembers<Method>()

    fun methodsWithReturnType(returnType: Class<*>) = listOf(returnType.fullName()).asMembers<Method>()

    fun methodsWithAnyParamAnnotated(annotation: Annotation) =
            methodsWithAnyParamAnnotated(annotation.annotationClass.java).filter {
                withAnyParameterAnnotation(it, annotation)
            }

    fun methodsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).asMembers<Method>()

    fun constructorsWithParamTypes(vararg types: Class<*>) = types.map { it.fullName() }.asMembers<Constructor<*>>()

    fun constructorsWithAnyParamAnnotated(annotation: Annotation) =
            constructorsWithAnyParamAnnotated(annotation.annotationType()).filter {
                withAnyParameterAnnotation(it, annotation)
            }

    fun constructorsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).asMembers<Constructor<*>>()

    private inline fun <reified T> List<Datum>.asMembers() =
            values(this).map(::descriptorToMember).filterIsInstance<T>()
}

class TypeElementsScanner(val includeFields: Boolean = true,
                          val includeMethods: Boolean = true,
                          val includeAnnotations: Boolean = true,
                          val publicOnly: Boolean = true) : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) {
        if (acceptResult(Datum(cls.name))) {
            store.put(Datum(cls.name), Datum(""))

            if (includeFields) {
                cls.fields.map { it.name }.forEach {
                    store.put(Datum(cls.name), Datum(it))
                }
            }

            if (includeMethods) {
                cls.methods.filter { method -> !publicOnly || method.isPublic }.forEach { method ->
                    store.put(Datum(cls.name), Datum("${method.name}(${method.parameters.joinToString()})"))
                }
            }

            if (includeAnnotations) {
                cls.annotations.forEach { annotation ->
                    store.put(Datum(cls.name), Datum("@$annotation"))
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
            cls.annotations.filter { acceptResult(Datum(it)) || it == Inherited::class.java.name }.forEach {
                store.put(Datum(it), Datum(cls.name))
            }
}


class SubTypesScanner(val excludeObjectClass: Boolean = true,
                      val expandSuperTypes: Boolean = true) : BaseClassScanner() {

    override fun acceptResult(fqn: Datum) = when {
        excludeObjectClass -> Filter.Exclude(Any::class.java.name).test(fqn.value)
        else               -> true
    }

    override fun scan(cls: ClassAdapter) {
        if (acceptResult(Datum(cls.superclass))) {
            store.put(Datum(cls.superclass), Datum(cls.name))
        }
        cls.interfaces.filter { acceptResult(Datum(it)) }.forEach {
            store.put(Datum(it), Datum(cls.name))
        }
    }

    override fun afterScan() {
        if (expandSuperTypes) expandSuperTypes()
    }

    /**
     * expand super types after scanning, for super types that were not scanned.
     * this is helpful in finding the transitive closure without scanning all 3rd party dependencies.
     * it uses [directParentsExceptObject].
     *
     *
     * for example, for classes A,B,C where A supertype of B, B supertype of C:
     *
     *  * if scanning C resulted in B (B->C in store), but A was not scanned (although A supertype of B) - then getSubTypes(A) will not return C
     *  * if expanding supertypes, B will be expanded with A (A->B in store) - then getSubTypes(A) will return C
     */
    fun expandSuperTypes() {
        val expand = Multimap<Datum, Datum>()
        (store.keys() - store.values()).forEach { key ->
            val type = classForName(key.value, defaultClassLoaders())
            if (type != null) expandSupertypes(expand, key, type)
        }
        store.putAll(expand)
    }

    private fun expandSupertypes(mmap: Multimap<in Datum, in Datum>, key: Datum, type: Class<*>): Unit =
            type.directParentsExceptObject().forEach { supertype ->
                if (mmap.put(supertype.fullName(), key)) {
                    logDebug("expanded subtype {} -> {}", supertype.name, key)
                    expandSupertypes(mmap, supertype.fullName(), supertype)
                }
            }

    /**
     * get all types scanned. this is effectively similar to getting all subtypes of Object.
     *
     * *note using this might be a bad practice. it is better to get types matching some criteria,
     * such as [subTypesOf] or [getTypesAnnotatedWith]*
     *
     * @return Set of String, and not of Class, in order to avoid definition of all types in PermGen
     */
    fun allTypes(): List<String> {
        val allTypes = recursiveValuesExcludingSelf(keys = listOf(Any::class.java.fullName()))
        return when {
            allTypes.isEmpty() -> throw RuntimeException("Couldn't find subtypes of Object. Make sure SubTypesScanner initialized to include Object class - new SubTypesScanner(false)")
            else               -> allTypes.map { it.value }
        }
    }

    fun <T> subTypesOf(type: Class<T>) =
            recursiveValuesExcludingSelf(listOf(type.fullName())).mapNotNull { classForName(it.value) as Class<out T>? }
}


fun descriptorToMember(value: Datum) = tryOrThrow("Can't resolve member $value") { descriptorToMemberOrThrow(value) }

fun descriptorToMemberOrThrow(datum: Datum): Member {
    val descriptor = datum.value
    val p0 = descriptor.lastIndexOf('(')
    val memberKey = if (p0 == -1) descriptor else descriptor.substringBeforeLast('(')
    val methodParameters = if (p0 == -1) "" else descriptor.substringAfterLast('(').substringBeforeLast(')')

    val p1 = Math.max(memberKey.lastIndexOf('.'), memberKey.lastIndexOf('$'))
    val className = memberKey.substring(memberKey.lastIndexOf(' ') + 1, p1)
    val memberName = memberKey.substring(p1 + 1)

    val parameterTypes = methodParameters.split(',').dropLastWhile { it.isEmpty() }.mapNotNull { name ->
        classForName(name.trim { it.toInt() <= ' '.toInt() })
    }.toTypedArray()

    return classForName(className)?.classHieararchy()?.asSequence()?.mapNotNull {
        tryOrNull {
            // todo: interface olsun olmasın getDeclared** metotları çalışıyor olmalı?
            when {
                it.isInterface -> {
                    when {
                        !descriptor.contains("(")    -> it.getField(memberName) as Member
                        descriptor.contains("init>") -> it.getConstructor(*parameterTypes) as Member
                        else                         -> it.getMethod(memberName, *parameterTypes) as Member
                    }
                }
                else           -> {
                    when {
                        !descriptor.contains("(")    -> it.getDeclaredField(memberName) as Member
                        descriptor.contains("init>") -> it.getDeclaredConstructor(*parameterTypes) as Member
                        else                         -> it.getDeclaredMethod(memberName, *parameterTypes) as Member
                    }
                }
            }
        }
    }?.firstOrNull() ?: throw RuntimeException(when {
                                                   descriptor.contains("(") -> "Can't resolve $memberName(${parameterTypes.joinToString()}) method for class $className"
                                                   else                     -> "Can't resolve $memberName field for class $className"
                                               })
}
