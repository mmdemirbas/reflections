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
import org.reflections.serializers.Serializer
import org.reflections.serializers.XmlSerializer
import org.reflections.toPrefixRegex
import org.reflections.util.Multimap
import org.reflections.util.annotationType
import org.reflections.util.classForName
import org.reflections.util.classHieararchy
import org.reflections.util.defaultClassLoaders
import org.reflections.util.directParentsExceptObject
import org.reflections.util.fullName
import org.reflections.util.logDebug
import org.reflections.util.logInfo
import org.reflections.util.logWarn
import org.reflections.util.runAllPossiblyParallelAndWait
import org.reflections.util.tryOrNull
import org.reflections.util.tryOrThrow
import org.reflections.util.urlForClass
import org.reflections.util.urlForPackage
import org.reflections.util.withAnnotation
import org.reflections.util.withAnyParameterAnnotation
import org.reflections.vfs.Vfs
import org.reflections.vfs.VfsDir
import org.reflections.vfs.VfsFile
import java.io.File
import java.lang.annotation.Inherited
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.regex.Pattern
import kotlin.system.measureTimeMillis


data class ScanMetrics(val elapsedTime: Int = 0, val keyCount: Int = 0, val valueCount: Int = 0) {
    constructor(metrics: List<ScanMetrics>) : this(metrics.sumBy { it.elapsedTime },
                                                   metrics.sumBy { it.keyCount },
                                                   metrics.sumBy { it.valueCount })
}

interface Scanner<S : Scanner<S>> {
    fun save(file: File, serializer: Serializer = XmlSerializer)

    fun scan(prefix: String, executorService: ExecutorService? = null) = scan(urls = urlForPackage(prefix),
                                                                              filter = Filter.Include(prefix.toPrefixRegex()),
                                                                              executorService = executorService)

    fun scan(klass: Class<*>, executorService: ExecutorService? = null) =
            scan(filter = Filter.Include(klass.name.replace("$", "\\$").replace(".", "\\.") + "(\\$.*)?"),
                 urls = setOf(urlForClass(klass)!!),
                 executorService = executorService)

    fun scan(urls: Collection<URL> = emptySet(),
             filter: Filter = Filter.Composite(emptyList()),
             executorService: ExecutorService? = null): S {
        if (urls.isEmpty()) throw RuntimeException("given scan urls are empty. set urls in the configuration")

        logDebug("going to scan these urls:\n{}", urls.joinToString("\n"))

        val scanMetrics =
                urls.map { url -> Callable<ScanMetrics> { scanUrl(filter, url) } }
                    .runAllPossiblyParallelAndWait(executorService)
        val afterScanMetrics = afterScan()
        val combinedMetrics = ScanMetrics(scanMetrics + afterScanMetrics)
        executorService?.shutdown()

        logInfo("Reflections took {} ms to scan {} urls, producing {} keys and {} values",
                combinedMetrics.elapsedTime,
                combinedMetrics.keyCount,
                combinedMetrics.valueCount)
        return this as S
    }

    fun scanUrl(filter: Filter, url: URL): ScanMetrics
    fun afterScan() = ScanMetrics()
}

data class CompositeScanner(val scanners: List<SimpleScanner<*>>) : Scanner<CompositeScanner> {
    constructor(vararg scanners: SimpleScanner<*>) : this(scanners.asList())

    override fun scanUrl(filter: Filter, url: URL) = ScanMetrics(scanners.map { it.scanUrl(filter, url) })

    override fun save(file: File, serializer: Serializer) = serializer.save(this, file).also {
        logInfo("Reflections successfully saved in ${file.absolutePath} using ${serializer.javaClass.simpleName}")
    }

    /**
     * get types annotated with a given annotation, both classes and annotations
     *
     * [java.lang.annotation.Inherited] is honored according to given honorInherited.
     *
     * when honoring @Inherited, meta-annotation should only effect annotated super classes and it's sub types
     *
     * when not honoring @Inherited, meta annotation effects all subtypes, including annotations interfaces and classes
     *
     * *Note that this (@Inherited) meta-annotation type has no effect if the annotated type is used for anything other then a class.
     * Also, this meta-annotation causes annotations to be inherited only from superclasses; annotations on implemented interfaces have no effect.*
     *
     * depends on TypeAnnotationsScanner and SubTypesScanner configured
     */
    fun typesAnnotatedWith(annotation: Class<out Annotation>,
                           honorInherited: Boolean,
                           classLoaders: List<ClassLoader> = defaultClassLoaders()): List<Class<*>> {
        val annotated = ask<TypeAnnotationsScanner> { values(annotation.fullName()) }
        val classes = getAllAnnotated(annotated, annotation.isAnnotationPresent(Inherited::class.java), honorInherited)
        return (annotated + classes).mapNotNull { classForName(it, classLoaders) }
    }

    /**
     * get types annotated with a given annotation, both classes and annotations, including annotation member values matching
     *
     * [java.lang.annotation.Inherited] is honored according to given honorInherited
     *
     * depends on TypeAnnotationsScanner and SubTypesScanner configured
     */
    fun typesAnnotatedWith(annotation: Annotation,
                           honorInherited: Boolean,
                           classLoaders: List<ClassLoader> = defaultClassLoaders()): List<Class<*>> {
        val annotated = ask<TypeAnnotationsScanner> { values(annotation.annotationClass.java.fullName()) }
        val filter = annotated.mapNotNull {
            classForName(it, classLoaders)
        }.filter { withAnnotation(it, annotation) }
        val classes =
                getAllAnnotated(filter.map { it.fullName() },
                                annotation.annotationType().isAnnotationPresent(Inherited::class.java),
                                honorInherited)
        return filter + classes.filter { it !in annotated }.mapNotNull {
            classForName(it, classLoaders)
        }
    }

    private fun getAllAnnotated(annotated: Collection<String>,
                                inherited: Boolean,
                                honorInherited: Boolean,
                                classLoaders: List<ClassLoader> = defaultClassLoaders()): Collection<String> {
        return when {
            !honorInherited -> {
                val keys = ask<TypeAnnotationsScanner> { recursiveValuesIncludingSelf(annotated) }
                ask<SubTypesScanner> { recursiveValuesIncludingSelf(keys) }
            }
            inherited       -> {
                val keys = annotated.filter { classForName(it, classLoaders)?.isInterface == false }
                val subTypes = ask<SubTypesScanner> { values(keys) }
                ask<SubTypesScanner> { recursiveValuesIncludingSelf(subTypes) }
            }
            else            -> annotated
        }
    }

    inline fun <reified S : SimpleScanner<*>> ask(flatMap: S.() -> Iterable<String>) =
            scanners.filterIsInstance<S>().apply {
                if (isEmpty()) throw RuntimeException("${S::class.java.simpleName} was not configured")
            }.flatMap(flatMap)

    companion object {
        /**
         * collect saved Reflections resources from all urls that contains the given [packagePrefix] and matches the given
         * [resourceNameFilter] and de-serializes them using the given serializer.
         *
         * by default, resources are collected from all urls that contains the package `META-INF/reflections`
         * and includes files matching the pattern `.*-reflections.xml`, and using [XmlSerializer].
         *
         * it is preferred to use a designated resource prefix (for example META-INF/reflections but not just META-INF),
         * so that relevant urls could be found much faster
         */
        fun collect(packagePrefix: String = "META-INF/reflections/",
                    resourceNameFilter: Filter = Filter.Include(".*-reflections\\.xml"),
                    serializer: Serializer = XmlSerializer): CompositeScanner {
            var urls: Collection<URL> = emptyList()
            var scanners: List<SimpleScanner<*>> = emptyList()

            val elapsedMillis = measureTimeMillis {
                urls = urlForPackage(packagePrefix)
                scanners = Vfs.findFiles(urls, packagePrefix, resourceNameFilter).flatMap { file ->
                    tryOrThrow("could not merge $file") {
                        file.openInputStream().use { stream -> serializer.read(stream).scanners }
                    }
                }
            }

            logInfo("Reflections took {} ms to collect {} urls, producing {} keys and %d values [{}]",
                    elapsedMillis,
                    urls.size,
                    scanners.sumBy(SimpleScanner<*>::keyCount),
                    scanners.sumBy(SimpleScanner<*>::valueCount),
                    urls.joinToString())

            return CompositeScanner(scanners)
        }
    }
}

abstract class SimpleScanner<S : SimpleScanner<S>> : Scanner<S> {
    val store = Multimap<String, String>()

    abstract fun acceptsInput(file: String): Boolean

    open fun acceptResult(fqn: String) = true

    override fun save(file: File, serializer: Serializer) = serializer.save(CompositeScanner(this), file).also {
        logInfo("Reflections successfully saved in ${file.absolutePath} using ${serializer.javaClass.simpleName}")
    }

    override fun scanUrl(filter: Filter, url: URL): ScanMetrics {
        val elapsedMillis = measureTimeMillis {
            Vfs.fromURL(url).use(VfsDir::files).forEach { file ->
                val fqn = file.relativePath!!.replace('/', '.')
                if (filter.test(file.relativePath!!) || filter.test(fqn)) {
                    try {
                        if (acceptsInput(file.relativePath!!) || acceptsInput(fqn)) {
                            scanFile(file)
                        }
                    } catch (e: Exception) {
                        logWarn("could not scan file {} in with scanner {}", file.relativePath, javaClass.simpleName, e)
                    }
                }
            }
        }
        return ScanMetrics(elapsedMillis.toInt())
    }

    abstract fun scanFile(vfsFile: VfsFile)

    override fun equals(o: Any?) = this === o || o != null && javaClass == o.javaClass
    override fun hashCode() = javaClass.hashCode()

    fun recursiveValuesIncludingSelf(keys: Collection<String>) = keys + recursiveValuesExcludingSelf(keys)

    fun recursiveValuesExcludingSelf(keys: Collection<String>): List<String> {
        val result = mutableListOf<String>()
        var values = values(keys)
        while (values.isNotEmpty()) {
            result += values
            values = values(values)
        }
        return result
    }

    fun keys() = store.keys()
    fun entries(): MutableSet<MutableMap.MutableEntry<String, MutableSet<String>>> = store.map.entries

    fun stringEntries() = store.map.entries

    fun keyCount(): Int = store.keyCount()
    fun valueCount(): Int = store.valueCount()

    fun values(keys: Collection<String>): Collection<String> = keys.flatMap(::values)
    fun values(): Collection<String> = store.values()
    fun values(key: String): Collection<String> = store.get(key).orEmpty()
    fun addEntry(key: String, value: String) {
        store.put(key, value)
    }
}


/**
 * collects all resources that are not classes in a collection
 *
 * key: value - {web.xml: WEB-INF/web.xml}
 */
class ResourceScanner : SimpleScanner<ResourceScanner>() {
    override fun acceptsInput(file: String) = !file.endsWith(".class") //not a class

    override fun scanFile(vfsFile: VfsFile) {
        addEntry(vfsFile.name, vfsFile.relativePath!!)
    }

    fun resources() = keys()

    /**
     * get resources relative paths where simple name (key) matches given regular expression
     *
     *  ```Set<String> xmls = resources(".*\\.xml");```
     */
    fun resources(pattern: Pattern) = resources { pattern.matcher(it).matches() }

    /**
     * get resources relative paths where simple name (key) matches given namePredicate
     */
    fun resources(namePredicate: (String) -> Boolean) = values(keys().filter { namePredicate(it) })
}

abstract class ClassScanner<S : ClassScanner<S>> : SimpleScanner<S>() {
    override fun acceptsInput(file: String) = file.endsWith(".class")

    override fun scanFile(vfsFile: VfsFile) =
            tryOrThrow("could not create class object from vfsFile ${vfsFile.relativePath!!}") {
                scanClass(CreateClassAdapter(vfsFile))
            }

    abstract fun scanClass(cls: ClassAdapter)
}

class FieldAnnotationsScanner : ClassScanner<FieldAnnotationsScanner>() {
    override fun scanClass(cls: ClassAdapter) = cls.fields.forEach { field ->
        field.annotations.filter { annotation -> acceptResult(annotation) }.forEach { annotation ->
            addEntry(annotation, "${cls.name}.${field.name}")
        }
    }

    fun fieldsAnnotatedWith(annotation: Annotation) = fieldsAnnotatedWith(annotation.annotationType()).filter {
        withAnnotation(it, annotation)
    }

    fun fieldsAnnotatedWith(annotation: Class<out Annotation>) = values(annotation.fullName()).map { field ->
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
class MemberUsageScanner(val classLoaders: List<ClassLoader> = defaultClassLoaders()) : ClassScanner<MemberUsageScanner>() {
    private val classPool by lazy {
        ClassPool().also { pool ->
            classLoaders.forEach { classLoader ->
                pool.appendClassPath(LoaderClassPath(classLoader))
            }
        }
    }

    override fun scanClass(cls: ClassAdapter) = tryOrThrow("Could not scan method usage for ${cls.name}") {
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
        if (acceptResult(key)) {
            addEntry(key, "$value #$lineNumber")
        }
    }

    val CtMember.declaringClassName get() = declaringClass.name
    val CtBehavior.parameterNames get() = JavassistMethodAdapter(methodInfo).parameters.joinToString()

    fun usages(o: AccessibleObject) = values(listOf(o.fullName())).map(::descriptorToMember)
}

class MethodAnnotationsScanner : ClassScanner<MethodAnnotationsScanner>() {
    override fun scanClass(cls: ClassAdapter) = cls.methods.forEach { method ->
        method.annotations.filter { annotation -> acceptResult(annotation) }
            .forEach { annotation -> addEntry(annotation, method.getMethodFullKey(cls)) }
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

    private inline fun <reified T> List<String>.methodAnnotations() =
            values(this).map { value -> descriptorToMember(value) }.filterIsInstance<T>()
}

class MethodParameterNamesScanner : ClassScanner<MethodParameterNamesScanner>() {
    override fun scanClass(cls: ClassAdapter) = cls.methods.forEach { method ->
        val key = method.getMethodFullKey(cls)
        if (acceptResult(key)) {
            val table =
                    (method as JavassistMethodAdapter).method.codeAttribute?.getAttribute(LocalVariableAttribute.tag) as LocalVariableAttribute?
            val length = table?.tableLength() ?: 0
            val startIndex = if (Modifier.isStatic(method.method.accessFlags)) 0 else 1 //skip this
            if (startIndex < length) {
                addEntry(key, (startIndex until length).joinToString {
                    method.method.constPool.getUtf8Info(table!!.nameIndex(it))
                })
            }
        }
    }

    fun paramNames(executable: Executable): List<String> {
        val names = values(executable.fullName())
        return when {
            names.isEmpty() -> emptyList()
            else            -> names.single().split(", ").dropLastWhile { it.isEmpty() }
        }
    }
}

/**
 * scans methods/constructors and indexes parameters, return type and parameter annotations
 */
class MethodParameterScanner : ClassScanner<MethodParameterScanner>() {
    override fun scanClass(cls: ClassAdapter) = cls.methods.forEach { method ->
        val signature = method.parameters.toString()
        if (acceptResult(signature)) {
            addEntry(signature, method.getMethodFullKey(cls))
        }

        val returnTypeName = method.returnType
        if (acceptResult(returnTypeName)) {
            addEntry(returnTypeName, method.getMethodFullKey(cls))
        }

        method.parameters.indices.flatMap { method.parameterAnnotations(it) }.filter {
            acceptResult(it)
        }.forEach { addEntry(it, method.getMethodFullKey(cls)) }
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

    private inline fun <reified T> List<String>.asMembers() =
            values(this).map(::descriptorToMember).filterIsInstance<T>()
}

class TypeElementsScanner(val includeFields: Boolean = true,
                          val includeMethods: Boolean = true,
                          val includeAnnotations: Boolean = true,
                          val publicOnly: Boolean = true) : ClassScanner<TypeElementsScanner>() {
    override fun scanClass(cls: ClassAdapter) {
        if (acceptResult(cls.name)) {
            addEntry(cls.name, "")

            if (includeFields) {
                cls.fields.map { it.name }.forEach {
                    addEntry(cls.name, it)
                }
            }

            if (includeMethods) {
                cls.methods.filter { method -> !publicOnly || method.isPublic }.forEach { method ->
                    addEntry(cls.name, "${method.name}(${method.parameters.joinToString()})")
                }
            }

            if (includeAnnotations) {
                cls.annotations.forEach { annotation ->
                    addEntry(cls.name, "@$annotation")
                }
            }
        }
    }
}

/**
 * scans for class's annotations, where @Retention(RetentionPolicy.RUNTIME)
 */
class TypeAnnotationsScanner : ClassScanner<TypeAnnotationsScanner>() {
    override fun scanClass(cls: ClassAdapter) =
            cls.annotations.filter { acceptResult(it) || it == Inherited::class.java.name }.forEach {
                addEntry(it, cls.name)
            }
}


class SubTypesScanner(val excludeObjectClass: Boolean = true,
                      val expandSuperTypes: Boolean = true) : ClassScanner<SubTypesScanner>() {

    override fun acceptResult(fqn: String) = when {
        excludeObjectClass -> Filter.Exclude(Any::class.java.name).test(fqn)
        else               -> true
    }

    override fun scanClass(cls: ClassAdapter) {
        if (acceptResult(cls.superclass)) {
            addEntry(cls.superclass, cls.name)
        }
        cls.interfaces.filter { acceptResult(it) }.forEach {
            addEntry(it, cls.name)
        }
    }

    override fun afterScan(): ScanMetrics {
        val elapsedMillis = measureTimeMillis {
            if (expandSuperTypes) expandSuperTypes()
        }
        return ScanMetrics(elapsedMillis.toInt())
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
        val expand = Multimap<String, String>()
        (keys() - values()).forEach { key ->
            val type = classForName(key, defaultClassLoaders())
            if (type != null) expandSupertypes(expand, key, type)
        }
        store.putAll(expand)
    }

    private fun expandSupertypes(mmap: Multimap<in String, in String>, key: String, type: Class<*>): Unit =
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
     * such as [subTypesOf] or [typesAnnotatedWith]*
     *
     * @return Set of String, and not of Class, in order to avoid definition of all types in PermGen
     */
    fun allTypes() = recursiveValuesExcludingSelf(keys = listOf(Any::class.java.fullName())).also {
        if (it.isEmpty()) throw RuntimeException("Couldn't find subtypes of Object. Make sure SubTypesScanner initialized to include Object class - new SubTypesScanner(false)")
    }

    fun <T> subTypesOf(type: Class<T>) =
            recursiveValuesExcludingSelf(listOf(type.fullName())).mapNotNull { classForName(it) as Class<out T>? }
}


fun descriptorToMember(value: String) = tryOrThrow("Can't resolve member $value") { descriptorToMemberOrThrow(value) }

fun descriptorToMemberOrThrow(descriptor: String): Member {
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
