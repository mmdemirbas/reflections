package com.mmdemirbas.reflections

import com.mmdemirbas.reflections.Vfs.findFiles
import javassist.ClassPool
import javassist.CtBehavior
import javassist.CtMember
import javassist.LoaderClassPath
import javassist.expr.*
import java.io.IOException
import java.lang.annotation.Inherited
import java.lang.reflect.*
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.regex.Pattern
import javax.servlet.ServletContext
import kotlin.system.measureTimeMillis

// todo: scan metric sağla, tarama süresi ve kaç key kaç value oluştuğu bilgisi verilebilir

sealed class ScanCommand {
    abstract fun toUrls(): List<URL>

    data class ScanClasspath(val classpath: String = System.getProperty("java.class.path").orEmpty(),
                             val fileSystem: FileSystem = FileSystems.getDefault(),
                             val pathSeparator: String = java.io.File.pathSeparator) : ScanCommand() {
        override fun toUrls() =
                classpath.split(pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.mapNotNull { path ->
                    tryOrNull { fileSystem.getPath(path).toUri().toURL() }
                }.distinctUrls().toList()
    }

    /**
     * Returns a distinct collection of URLs based on URLs derived from class loaders.
     *
     *
     * This finds the URLs using [URLClassLoader.getURLs] using the specified
     * class loader, searching up the parent hierarchy.
     *
     *
     * If the optional [ClassLoader]s are not specified, then both [contextClassLoader]
     * and [staticClassLoader] are used for [ClassLoader.getResources].
     *
     *
     * The returned URLs retains the order of the given `classLoaders`.
     *
     * @return the collection of URLs, not null
     */
    data class ScanClassloader(val classLoaders: Collection<ClassLoader?> = defaultClassLoaders()) : ScanCommand() {
        override fun toUrls() = classLoaders.flatMap { loader ->
            loader.generateWhileNotNull { parent }
        }.filterIsInstance<URLClassLoader>().flatMap { it.urLs.orEmpty().asList() }.distinctUrls().toList()
    }

    /**
     * Returns a distinct collection of URLs based on a resource.
     *
     *
     * This searches for the resource name, using [ClassLoader.getResources].
     * For example, `urlForResource(test.properties)` effectively returns URLs from the
     * classpath containing files of that name.
     *
     *
     * If the optional [ClassLoader]s are not specified, then both [contextClassLoader]
     * and [staticClassLoader] are used for [ClassLoader.getResources].
     *
     *
     * The returned URLs retains the order of the given `classLoaders`.
     *
     * @return the collection of URLs, not null
     */
    data class ScanResource(val resourceName: String,
                            val classLoaders: Collection<ClassLoader> = defaultClassLoaders()) : ScanCommand() {
        override fun toUrls(): List<URL> {
            return classLoaders.flatMap { classLoader ->
                try {
                    classLoader.getResources(resourceName).toList().map { url ->
                        val externalForm = url.toExternalForm()
                        when {
                            externalForm.contains(resourceName) -> // Add old url as contextUrl to support exotic url handlers
                                URL(url, externalForm.substringBeforeLast(resourceName))
                            else                                -> url
                        }
                    }
                } catch (e: IOException) {
                    logger.error("error getting resources for $resourceName", e)
                    emptyList<URL>()
                }
            }.distinctUrls().toList()
        }
    }

    /**
     * Returns a distinct collection of URLs based on a package name.
     *
     *
     * This searches for the package name as a resource, using [ClassLoader.getResources].
     * For example, `urlForPackage(org.reflections)` effectively returns URLs from the
     * classpath containing packages starting with `org.reflections`.
     *
     *
     * If the optional [ClassLoader]s are not specified, then both [contextClassLoader]
     * and [staticClassLoader] are used for [ClassLoader.getResources].
     *
     *
     * The returned URLs retainsthe order of the given `classLoaders`.
     *
     * @return the collection of URLs, not null
     */
    data class ScanPackage(val name: String,
                           val classLoaders: List<ClassLoader> = defaultClassLoaders()) : ScanCommand() {
        override fun toUrls() =
                ScanResource(name.replace(".", "/").replace("\\", "/").removePrefix("/"), classLoaders).toUrls()
    }

    /**
     * Returns the URL that contains a `Class`.
     *
     *
     * This searches for the class using [ClassLoader.getResource].
     *
     *
     * If the optional [ClassLoader]s are not specified, then both [contextClassLoader]
     * and [staticClassLoader] are used for [ClassLoader.getResources].
     *
     * @return the URL containing the class, null if not found
     */
    data class ScanClass(val aClass: Class<*>, val classLoaders: Collection<ClassLoader> = defaultClassLoaders()) :
            ScanCommand() {
        override fun toUrls() = listOf(toUrl())

        fun toUrl(): URL {
            val resourceName = aClass.name.replace(".", "/") + ".class"
            return classLoaders.mapNotNull {
                try {
                    val url = it.getResource(resourceName)
                    when (url) {
                        null -> null
                        else -> URL(url.toExternalForm().substringBeforeLast(aClass.getPackage().name.replace(".",
                                                                                                              "/")))
                    }
                } catch (e: MalformedURLException) {
                    logger.warn("Could not get URL", e)
                    null
                }
            }.firstOrNull()
                   ?: throw RuntimeException("Couldn't find ${aClass.name} in any of the class loaders: $classLoaders")
        }
    }

    data class ScanUrl(val urls: List<URL>) : ScanCommand() {
        override fun toUrls() = urls
    }

    /**
     * Returns a distinct collection of URLs based on the `WEB-INF/lib` folder.
     *
     *
     * This finds the URLs using the [javax.servlet.ServletContext].
     *
     *
     * The returned URLs retains the order of the given `classLoaders`.
     *
     * @return the collection of URLs, not null
     */
    data class ScanWebInfLib(val servletContext: ServletContext) : ScanCommand() {
        override fun toUrls() = servletContext.getResourcePaths("/WEB-INF/lib").orEmpty().mapNotNull {
            tryOrNull {
                servletContext.getResource(it as String)
            }
        }.distinctUrls().toList()
    }

    /**
     * Returns the URL of the `WEB-INF/classes` folder.
     *
     *
     * This finds the URLs using the [javax.servlet.ServletContext].
     *
     * @return the collection of URLs, not null
     */

    data class ScanWebInfClasses(val servletContext: ServletContext,
                                 val fileSystem: FileSystem = FileSystems.getDefault()) : ScanCommand() {
        override fun toUrls() = listOf(toUrl())

        fun toUrl(): URL {
            val path = servletContext.getRealPath("/WEB-INF/classes")
            return if (path == null) servletContext.getResource("/WEB-INF/classes")
            else {
                val file = fileSystem.getPath(path)
                if (Files.exists(file)) file.toUri().toURL() else throw  RuntimeException()
            }
        }
    }
}


interface Scanner<S : Scanner<S>> {
    fun save(path: Path, serializer: Serializer = XmlSerializer()): S
    fun dump(serializer: Serializer = JsonSerializer()): S

    // todo: sınıflar adreslenirken yüklendikleri classLoader'lar da dahil edilse daha gerçekçi olur.
    // todo: bir sınıf tek bir classloader'da bulununca bırakılmalı mı? Sanki hepsine bakılsa daha doğru olmaz mı?

    fun scan(prefix: String,
             fileSystem: FileSystem = FileSystems.getDefault(),
             executorService: ExecutorService? = null) = scan(urls = ScanCommand.ScanPackage(prefix).toUrls(),
            // todo: filter'lar öncelikle PackageStartsWith gibi custom tiplerle zenginleştirilmeli, en son regex olabilir
                                                              filter = Filter.Include(prefix.toPrefixRegex()),
                                                              fileSystem = fileSystem,
                                                              executorService = executorService)

    fun scan(klass: Class<*>,
             fileSystem: FileSystem = FileSystems.getDefault(),
             executorService: ExecutorService? = null) = scan(urls = ScanCommand.ScanClass(klass).toUrls(),
                                                              filter = Filter.Include(klass.name.fqnToResourceName()),
                                                              fileSystem = fileSystem,
                                                              executorService = executorService)

    fun scan(urls: Collection<URL> = emptySet(),
             filter: Filter = Filter.Composite(emptyList()),
             fileSystem: FileSystem = FileSystems.getDefault(),
             executorService: ExecutorService? = null): S {
        // todo: thread'lerin top-level'ında bir exception olduğunda sonucun açık seçik belirtilmesi gerek. ortak mekanizma düşün.
        urls.map { url ->
            Runnable {
                try {
                    scanUrl(url, filter, fileSystem)
                } catch (e: Exception) {
                    logger.error("Error while scanning $url", e)
                    throw e
                }
            }
        }.runAllPossiblyParallelAndWait(executorService)
        executorService?.shutdown()
        return this as S
    }

    fun scanUrl(url: URL, filter: Filter, fileSystem: FileSystem = FileSystems.getDefault())

    fun afterScan() = Unit
}

data class CompositeScanner(val scanners: List<SimpleScanner<*>>) : Scanner<CompositeScanner> {
    constructor(vararg scanners: SimpleScanner<*>) : this(scanners.asList())

    override fun scanUrl(url: URL, filter: Filter, fileSystem: FileSystem) =
            scanners.forEach { it.scanUrl(url, filter, fileSystem) }

    override fun save(path: Path, serializer: Serializer): CompositeScanner {
        serializer.write(this, path).also {
            logger.info("Reflections successfully saved in ${path.toAbsolutePath()} using ${serializer.javaClass.simpleName}")
        }
        return this
    }

    override fun dump(serializer: Serializer): CompositeScanner {
        println(serializer.toString(this))
        return this
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
        // todo: tek bir dosyayı geri yükleyebilme desteği de sağlanmalı
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
                    serializer: Serializer = XmlSerializer(),
                    fileSystem: FileSystem = FileSystems.getDefault()): CompositeScanner {
            var urls: Collection<URL> = emptyList()
            var scanners: List<SimpleScanner<*>> = emptyList()

            // todo: performans için list yerine sequence kullanımı değerlendirilebilir genel olarak
            val elapsedMillis = measureTimeMillis {
                urls = ScanCommand.ScanPackage(packagePrefix).toUrls()
                scanners = findFiles(inUrls = urls, fileSystem = fileSystem).filter {
                    it.relativePath.throwIfNull("relativePath of virtualFile: $it").startsWith(packagePrefix) && resourceNameFilter.acceptFile(
                            it)
                }.toList().flatMap { file ->
                    tryOrThrow("could not merge $file") {
                        file.openInputStream().bufferedReader().use { stream -> serializer.read(stream).scanners }
                    }
                }
            }

            logger.info("Reflections took {} ms to collect {} urls, producing {} keys and %d values [{}]",
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

    override fun save(path: Path, serializer: Serializer): S {
        CompositeScanner(this).save(path, serializer)
        return this as S
    }

    override fun dump(serializer: Serializer): S {
        CompositeScanner(this).dump(serializer)
        return this as S
    }

    override fun scanUrl(url: URL, filter: Filter, fileSystem: FileSystem) {
        Vfs.fromURL(url = url, fileSystem = fileSystem).use(VirtualDir::files).forEach { vfsFile ->
            //   todo:             if (filter.acceptFile(vfsFile))
            tryOrThrow("could not scan file ${vfsFile.relativePath} with scanner ${javaClass.simpleName}") {
                scanFile(vfsFile)
            }
        }
    }

    abstract fun scanFile(virtualFile: VirtualFile)

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
    fun entries() = store.map.entries
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
    override fun scanFile(virtualFile: VirtualFile) {
        val relativePath = virtualFile.relativePath.throwIfNull("relativePath of virtualFile: $virtualFile")
        if (!relativePath.endsWith(".class")) {
            addEntry(virtualFile.name, relativePath)
        }
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
    override fun scanFile(virtualFile: VirtualFile) {
        val relativePath = virtualFile.relativePath.throwIfNull("relativePath of virtualFile: $virtualFile")
        if (relativePath.endsWith(".class")) {
            tryOrThrow("could not create class object from virtualFile $relativePath") {
                // todo: multiple scanners varsa her scanner aynı file'ı yeniden yüklüyor, efficient bir api sağlanabilir
                scanClass(createClassAdapter(virtualFile))
            }
        }
    }

    abstract fun scanClass(cls: ClassAdapter)
}

class FieldAnnotationsScanner : ClassScanner<FieldAnnotationsScanner>() {
    override fun scanClass(cls: ClassAdapter) {
        cls.fields.forEach { field ->
            field.annotations.forEach { annotation ->
                addEntry(annotation, "${cls.name}.${field.name}")
            }
        }
    }

    fun fieldsAnnotatedWith(annotation: Annotation) =
            fieldsAnnotatedWith(annotation.annotationType()).filter { withAnnotation(it, annotation) }

    fun fieldsAnnotatedWith(annotation: Class<out Annotation>): List<Field> {
        val annotationName = annotation.fullName()
        val fields = values(annotationName)
        return fields.map { field ->
            val className = field.substringBeforeLast('.')
            val fieldName = field.substringAfterLast('.')
            tryOrThrow("Can't resolve field named $fieldName") {
                classForName(className).throwIfNull("classForName($className)").getDeclaredField(fieldName)
            }
        }
    }
}

// todo: Javassist'e hard-coded depend eden scanner'lar ya refactory vs edilmeli mümkünse... ya da bu dependency'yi mümkün kılan legal bir yöntem bulunmalı

/**
 * scans methods/constructors/fields usage.
 *
 * depends on [org.reflections.adapters.CreateJavassistClassAdapter] configured
 */
class MemberUsageScanner(val classLoaders: List<ClassLoader> = defaultClassLoaders()) : ClassScanner<MemberUsageScanner>() {
    private val classPool by lazy {
        ClassPool().also { pool ->
            classLoaders.forEach { classLoader -> pool.appendClassPath(LoaderClassPath(classLoader)) }
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
        val key = "${member.declaringClassName}.${member.methodInfo.name}(${member.parameterTypeNames})"
        member.instrument(object : ExprEditor() {
            override fun edit(e: NewExpr?) = tryOrThrow("Could not find new instance usage in $key") {
                addEntry("${e!!.constructor.declaringClassName}.<init>(${e.constructor.parameterTypeNames})",
                         "$key #${e.lineNumber}")
            }

            override fun edit(m: MethodCall?) = tryOrThrow("Could not find member ${m!!.className} in $key") {
                addEntry("${m.method.declaringClassName}.${m.methodName}(${m.method.parameterTypeNames})",
                         "$key #${m.lineNumber}")
            }

            override fun edit(c: ConstructorCall?) = tryOrThrow("Could not find member ${c!!.className} in $key") {
                addEntry("${c.constructor.declaringClassName}.<init>(${c.constructor.parameterTypeNames})",
                         "$key #${c.lineNumber}")
            }

            override fun edit(f: FieldAccess?) = tryOrThrow("Could not find member ${f!!.fieldName} in $key") {
                addEntry("${f.field.declaringClassName}.${f.fieldName}", "$key #${f.lineNumber}")
            }

            // todo: diğer usage tipleri de inspect edilebilir: cast, instanceof vs...
        })
    }

    val CtBehavior.parameterTypeNames
        get() = methodInfo.asAdapter(declaringClassName).params.joinToString(",") { it.type }

    val CtMember.declaringClassName get() = declaringClass.name

    fun usages(o: AccessibleObject) = values(listOf(o.fullName())).map(::descriptorToMember)
}

class MethodAnnotationsScanner : ClassScanner<MethodAnnotationsScanner>() {
    override fun scanClass(cls: ClassAdapter) = cls.methods.forEach { method ->
        method.annotations.forEach { annotation -> addEntry(annotation, method.signature) }
    }

    fun constructorsAnnotatedWith(annotation: Annotation) =
            constructorsAnnotatedWith(annotation.annotationType()).filter { withAnnotation(it, annotation) }

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
    // todo: scanner tipine göre kendi data type'ını tanımlasak daha kullanışlı olabilir.
    override fun scanClass(cls: ClassAdapter) = cls.methods.forEach { method ->
        addEntry(method.signature, method.params.joinToString(",") { it.name ?: "?" })
    }

    fun paramNames(executable: Executable): List<String> {
        val names = values(executable.fullName())
        return when {
            names.isEmpty() -> emptyList()
            else            -> names.single().split(',').dropLastWhile { it.isEmpty() }
        }
    }
}

class MethodParameterTypesScanner : ClassScanner<MethodParameterTypesScanner>() {
    override fun scanClass(cls: ClassAdapter) = cls.methods.forEach { method ->
        addEntry(method.params.joinToString(",") { it.type }, method.signature)
    }

    fun methodsWithParamTypes(vararg types: Class<*>) = valuesByType<Method>(types.map { it.fullName() })

    fun constructorsWithParamTypes(vararg types: Class<*>) =
            valuesByType<Constructor<*>>(listOf(types.joinToString(",") { it.fullName() }))

    private inline fun <reified T> valuesByType(keys: List<String>) =
            values(keys).map(::descriptorToMember).filterIsInstance<T>()
}

class MethodReturnTypesScanner : ClassScanner<MethodReturnTypesScanner>() {
    override fun scanClass(cls: ClassAdapter) = cls.methods.forEach { method ->
        addEntry(method.returnType, method.signature)
    }

    fun methodsWithReturnType(returnType: Class<*>) = valuesByType<Method>(listOf(returnType.fullName()))

    private inline fun <reified T> valuesByType(keys: List<String>) =
            values(keys).map(::descriptorToMember).filterIsInstance<T>()
}

class MethodParameterAnnotationsScanner : ClassScanner<MethodParameterAnnotationsScanner>() {
    override fun scanClass(cls: ClassAdapter) = cls.methods.forEach { method ->
        method.params.forEach { parameter ->
            parameter.annotations.forEach { annotation ->
                addEntry(annotation, method.signature)
            }
        }
    }

    fun methodsWithAnyParamAnnotated(annotation: Annotation) =
            methodsWithAnyParamAnnotated(annotation.annotationClass.java).filter {
                withAnyParameterAnnotation(it, annotation)
            }

    fun methodsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            valuesByType<Method>(listOf(annotation.fullName()))

    fun constructorsWithAnyParamAnnotated(annotation: Annotation) =
            constructorsWithAnyParamAnnotated(annotation.annotationType()).filter {
                withAnyParameterAnnotation(it, annotation)
            }

    fun constructorsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            valuesByType<Constructor<*>>(listOf(annotation.fullName()))

    private inline fun <reified T> valuesByType(keys: List<String>) =
            values(keys).map(::descriptorToMember).filterIsInstance<T>()
}

class TypeElementsScanner(val includeFields: Boolean = true,
                          val includeMethods: Boolean = true,
                          val includeAnnotations: Boolean = true) : ClassScanner<TypeElementsScanner>() {
    // todo: bunun kullanım alanı var mı? orjinalinde testi var mı? 3 scanner'a parçalanabilir
    override fun scanClass(cls: ClassAdapter) {
        addEntry(cls.name, "")

        if (includeFields) {
            cls.fields.map { it.name }.forEach {
                addEntry(cls.name, it)
            }
        }

        if (includeMethods) {
            cls.methods.forEach { method ->
                addEntry(cls.name, "${method.name}(${method.params.joinToString(",") { it.type }}")
            }
        }

        if (includeAnnotations) {
            cls.annotations.forEach { annotation ->
                addEntry(cls.name, "@$annotation")
            }
        }
    }
}

/**
 * scans for class's annotations, where @Retention(RetentionPolicy.RUNTIME)
 */
class TypeAnnotationsScanner : ClassScanner<TypeAnnotationsScanner>() {
    override fun scanClass(cls: ClassAdapter) = cls.annotations.forEach { addEntry(it, cls.name) }
    // todo: query method'larını class'ın içine taşı
}


class SubTypesScanner(val excludeObjectClass: Boolean = true,
                      val expandSuperTypes: Boolean = true) : ClassScanner<SubTypesScanner>() {
    override fun scanClass(cls: ClassAdapter) {
        addEntryIfAccepted(cls.superclass, cls.name)
        cls.interfaces.forEach { addEntryIfAccepted(it, cls.name) }
    }

    private fun addEntryIfAccepted(fqn: String, value: String) {
        // todo: fqn yerine file veya başka bir domain object kullanmanın yolunu araştır
        if (!excludeObjectClass || Filter.Exclude(Any::class.java.name).acceptFqn(fqn)) {
            addEntry(fqn, value)
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
        val expand = Multimap<String, String>()
        (keys() - values()).forEach { key ->
            val type = classForName(key, defaultClassLoaders())
            if (type != null) expandSupertypes(expand, key, type)
        }
        store.putAll(expand)
    }

    private fun expandSupertypes(mmap: Multimap<in String, in String>, key: String, type: Class<*>) {
        type.directParentsExceptObject().forEach { supertype ->
            if (mmap.put(supertype.fullName(), key)) {
                logger.debug("expanded subtype {} -> {}", supertype.name, key)
                expandSupertypes(mmap, supertype.fullName(), supertype)
            }
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


private fun descriptorToMember(value: String) = tryOrThrow("Can't resolve member $value") {
    val memberFullName = value.substringBeforeLast('(')
    val paramTypeNames = value.substringAfterLast('(', "").substringBeforeLast(')')
    val className = memberFullName.substringBeforeLast('.')
    val memberName = memberFullName.substringAfterLast('.')
    val paramTypes = when {
        paramTypeNames.isEmpty() -> emptyArray()
        else                     -> paramTypeNames.split(',').mapNotNull { type -> classForName(type.trim { it.toInt() <= ' '.toInt() }) }.toTypedArray()
    }
    classForName(className)?.classHieararchy()?.asSequence()?.mapNotNull {
        tryOrNull {
            when {
                !value.contains("(")    -> it.getDeclaredField(memberName) as Member
                value.contains("init>") -> it.getDeclaredConstructor(*paramTypes) as Member
                else                    -> it.getDeclaredMethod(memberName, *paramTypes) as Member
            }
        }
    }?.firstOrNull() ?: throw RuntimeException(when {
                                                   value.contains("(") -> "Can't resolve $memberName(${paramTypes.joinToString(
                                                           ",")}) method for class $className"
                                                   else                -> "Can't resolve $memberName field for class $className"
                                               })
}