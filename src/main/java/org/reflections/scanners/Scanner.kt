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
import org.reflections.util.defaultClassLoaders
import org.reflections.util.directParentsExceptObject
import org.reflections.util.fullName
import org.reflections.util.logDebug
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

    fun values(keys: Collection<Datum>): Collection<Datum> = when {
        keys.isEmpty() -> values()
        else           -> keys.flatMap(::values)
    }

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
     * depends on ResourcesScanner configured
     * ```Set<String> xmls = reflections.resources(".*\\.xml");```
     */
    fun resources(pattern: Pattern) = resources { pattern.matcher(it).matches() }

    /**
     * get resources relative paths where simple name (key) matches given namePredicate
     *
     * depends on ResourcesScanner configured
     */
    fun resources(namePredicate: (String) -> Boolean) = values(store.keys().filter { namePredicate(it.value) }).toSet()
}

abstract class BaseClassScanner : Scanner() {
    override fun acceptsInput(file: String) = file.endsWith(".class")

    override fun scan(vfsFile: VfsFile, classObject: ClassAdapter?): ClassAdapter {
        return (classObject ?: tryOrThrow("could not create class object from vfsFile ${vfsFile.relativePath!!}") {
            CreateClassAdapter(vfsFile)
        }).also {
            scan(it)
        }
    }

    abstract fun scan(cls: ClassAdapter)
}

class FieldAnnotationsScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) = cls.fields.forEach { field ->
        field.annotations.filter { annotation -> acceptResult(Datum(annotation)) }.forEach { annotation ->
            store.put(Datum(annotation), Datum("${cls.name}.${field.name}"))
        }
    }

    /**
     * get all methods annotated with a given annotation, including annotation member values matching
     *
     * depends on FieldAnnotationsScanner configured
     */
    fun fieldsAnnotatedWith(annotation: Annotation) = fieldsAnnotatedWith(annotation.annotationType()).filter {
        withAnnotation(it, annotation)
    }.toSet()

    /**
     * get all fields annotated with a given annotation
     *
     * depends on FieldAnnotationsScanner configured
     */
    fun fieldsAnnotatedWith(annotation: Class<out Annotation>) = values(annotation.fullName()).map {
        val field = it.value
        val className = field.substringBeforeLast('.')
        val fieldName = field.substringAfterLast('.')
        tryOrThrow("Can't resolve field named $fieldName") {
            classForName(className)!!.getDeclaredField(fieldName)
        }
    }.toSet()
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

    /**
     * get all given `constructor`, `method`, or `field` usages in methods and constructors
     *
     * depends on MemberUsageScanner configured
     */
    fun usages(o: AccessibleObject) = values(listOf(o.fullName())).map(::descriptorToMember)
}

class MethodAnnotationsScanner : BaseClassScanner() {
    override fun scan(cls: ClassAdapter) = cls.methods.forEach { method ->
        method.annotations.filter { annotation -> acceptResult(Datum(annotation)) }
            .forEach { annotation -> store.put(Datum(annotation), method.getMethodFullKey(cls)) }
    }

    /**
     * get all constructors annotated with a given annotation, including annotation member values matching
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun constructorsAnnotatedWith(annotation: Annotation) =
            constructorsAnnotatedWith(annotation.annotationType()).filter {
                withAnnotation(it, annotation)
            }.toSet()

    /**
     * get all constructors annotated with a given annotation
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun constructorsAnnotatedWith(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodAnnotations().filterIsInstance<Constructor<*>>().toSet()

    /**
     * get all methods annotated with a given annotation, including annotation member values matching
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun methodsAnnotatedWith(annotation: Annotation) =
            listOf(annotation.annotationClass.java.fullName()).methodAnnotations().filterIsInstance<Method>().filter {
                withAnnotation(it, annotation)
            }.toSet()

    /**
     * get all methods annotated with a given annotation
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun methodsAnnotatedWith(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodAnnotations().filterIsInstance<Method>().toSet()

    private fun List<Datum>.methodAnnotations() = values(this).map { value -> descriptorToMember(value) }
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

    /* get parameter names of given `method` or `constructor`
    *
    * depends on MethodParameterNamesScanner configured
    */
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
        val signature = Datum(method.parameters.joinToString())
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

    /**
     * get methods with parameter types matching given `types`
     */
    fun methodsMatchParams(vararg types: Class<*>) =
            types.map { it.fullName() }.methodParams().filterIsInstance<Method>().toSet()

    /**
     * get methods with return type match given type
     */
    fun methodsReturn(returnType: Class<*>) =
            listOf(returnType.fullName()).methodParams().filterIsInstance<Method>().toSet()

    /**
     * get methods with any parameter annotated with given annotation, including annotation member values matching
     */
    fun methodsWithAnyParamAnnotated(annotation: Annotation) =
            methodsWithAnyParamAnnotated(annotation.annotationClass.java).filter {
                withAnyParameterAnnotation(it, annotation)
            }.toSet()

    /**
     * get methods with any parameter annotated with given annotation
     */
    fun methodsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodParams().filterIsInstance<Method>().toSet()

    /**
     * get constructors with parameter types matching given `types`
     */
    fun constructorsMatchParams(vararg types: Class<*>) =
            types.map { it.fullName() }.methodParams().filterIsInstance<Constructor<*>>().toSet()

    /**
     * get constructors with any parameter annotated with given annotation, including annotation member values matching
     */
    fun constructorsWithAnyParamAnnotated(annotation: Annotation) =
            constructorsWithAnyParamAnnotated(annotation.annotationType()).filter {
                withAnyParameterAnnotation(it, annotation)
            }.toSet()

    /**
     * get constructors with any parameter annotated with given annotation
     */
    fun constructorsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodParams().filterIsInstance<Constructor<*>>().toSet()

    private fun List<Datum>.methodParams() = values(this).map(::descriptorToMember)

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
                //as an exception, accept Inherited as well
                store.put(Datum(it), Datum(cls.name))
            }
}


/**
 * @property excludeObjectClass
 * @property expandSuperTypes if true (default), expand super types after scanning, for super types that were not scanned.
 */
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
        if (expandSuperTypes) {
            expandSuperTypes()
        }
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
     * depends on SubTypesScanner configured with `SubTypesScanner(false)`, otherwise `RuntimeException` is thrown
     *
     * *note using this might be a bad practice. it is better to get types matching some criteria,
     * such as [subTypesOf] or [getTypesAnnotatedWith]*
     *
     * @return Set of String, and not of Class, in order to avoid definition of all types in PermGen
     */
    fun allTypes(): Set<String> {
        val allTypes = recursiveValuesExcludingSelf(keys = listOf(Any::class.java.fullName())).toSet()
        return when {
            allTypes.isEmpty() -> throw RuntimeException("Couldn't find subtypes of Object. Make sure SubTypesScanner initialized to include Object class - new SubTypesScanner(false)")
            else               -> allTypes.map { it.value }.toSet()
        }
    }

    /**
     * gets all sub types in hierarchy of a given type
     *
     * depends on SubTypesScanner configured
     */
    fun <T> subTypesOf(type: Class<T>) =
            recursiveValuesExcludingSelf(listOf(type.fullName())).mapNotNull { classForName(it.value) as Class<out T>? }.toSet()

}


fun descriptorToMember(value: Datum) =
        tryOrThrow("Can't resolve member named $value") { descriptorToMemberOrThrow(value) }

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

    var aClass = classForName(className)
    while (aClass != null) {
        try {
            return when {
                !descriptor.contains("(")    -> {
                    when {
                        aClass.isInterface -> aClass.getField(memberName)
                        else               -> aClass.getDeclaredField(memberName)
                    }
                }
                descriptor.contains("init>") -> {
                    when {
                        aClass.isInterface -> aClass.getConstructor(*parameterTypes)
                        else               -> aClass.getDeclaredConstructor(*parameterTypes)
                    }
                }
                else                         -> {
                    when {
                        aClass.isInterface -> aClass.getMethod(memberName, *parameterTypes)
                        else               -> aClass.getDeclaredMethod(memberName, *parameterTypes)
                    }
                }
            }
        } catch (e: Exception) {
            aClass = aClass.superclass
        }
    }
    when {
        descriptor.contains("(") -> throw RuntimeException("Can't resolve $memberName(${parameterTypes.joinToString()}) method for class $className")
        else                     -> throw RuntimeException("Can't resolve $memberName field for class $className")
    }
}
