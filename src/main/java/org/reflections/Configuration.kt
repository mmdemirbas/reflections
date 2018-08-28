package org.reflections

import org.reflections.adapters.ClassAdapter
import org.reflections.scanners.FieldAnnotationsScanner
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.ResourcesScanner
import org.reflections.scanners.Scanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.serializers.Serializer
import org.reflections.serializers.XmlSerializer
import org.reflections.util.Datum
import org.reflections.util.annotationType
import org.reflections.util.classForName
import org.reflections.util.defaultClassLoaders
import org.reflections.util.fullName
import org.reflections.util.logDebug
import org.reflections.util.logInfo
import org.reflections.util.logWarn
import org.reflections.util.tryOrThrow
import org.reflections.util.urlForPackage
import org.reflections.util.withAnnotation
import org.reflections.vfs.Vfs
import org.reflections.vfs.VfsDir
import org.reflections.vfs.VfsFile
import java.io.File
import java.lang.annotation.Inherited
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.regex.Pattern

/**
 * @property scanners  the scanner instances used for scanning different metadata
 * @property urls the urls to be scanned
 * @property filter the fully qualified name filter used to filter types to be scanned
 * @property executorService executor service used to scan files. if null, scanning is done in a simple for loop
 * @property classLoaders the class loaders, might be used for resolving methods/fields
 * @constructor
 */
data class Configuration(val scanners: Set<Scanner> = setOf(TypeAnnotationsScanner(), SubTypesScanner()),
                         val urls: Set<URL> = emptySet(),
                         val filter: Filter = Filter.Composite(emptyList()),
                         val executorService: ExecutorService? = null,
                         val classLoaders: List<ClassLoader> = defaultClassLoaders()) {
    /**
     * a convenient constructor for scanning within a package prefix.
     *
     * this actually create a [Configuration] with:
     * <br></br> - urls that contain resources with name `prefix`
     * <br></br> - filterInputsBy where name starts with the given `prefix`
     * <br></br> - scanners set to the given `scanners`, otherwise defaults to [TypeAnnotationsScanner] and [SubTypesScanner].
     *
     * @param prefix   package prefix, to be used with [urlForPackage] )}
     * @param scanners optionally supply scanners, otherwise defaults to [TypeAnnotationsScanner] and [SubTypesScanner]
     */
    constructor(prefix: String, vararg scanners: Scanner = arrayOf(TypeAnnotationsScanner(), SubTypesScanner())) : this(
            filter = Filter.Include(prefix.toPrefixRegex()),
            urls = urlForPackage(prefix),
            scanners = scanners.toSet())

    fun withScan(): Configuration {
        scan()
        return this
    }

    private fun scan() {
        if (urls.isEmpty()) {
            logWarn("given scan urls are empty. set urls in the configuration")
            return
        }

        logDebug("going to scan these urls:\n{}", urls.joinToString("\n"))

        val startTime = System.currentTimeMillis()
        var scannedUrls = 0
        val executorService = executorService
        val futures = mutableListOf<Future<*>>()

        // todo: change flow or structure: for each url -> for each file -> for each scanner

        urls.forEach { url ->
            try {
                when (executorService) {
                    null -> scanUrl(url)
                    else -> futures.add(executorService.submit {
                        logDebug("[{}] scanning {}", Thread.currentThread(), url)
                        scanUrl(url)
                    })
                }
                scannedUrls++
            } catch (e: RuntimeException) {
                logWarn("could not create VfsDir from url. ignoring the exception and continuing", e)
            }
        }

        //todo use CompletionService
        futures.forEach { it.get() }

        scanners.forEach { it.afterScan() }

        val elapsedTime = System.currentTimeMillis() - startTime

        //gracefully shutdown the parallel scanner executor service.
        executorService?.shutdown()

        logInfo("Reflections took {} ms to scan {} urls, producing {} keys and {} values {}",
                elapsedTime,
                scannedUrls,
                scanners.sumBy(Scanner::keyCount),
                scanners.sumBy(Scanner::valueCount),
                if (executorService is ThreadPoolExecutor) "[using ${executorService.maximumPoolSize} cores]" else "")
    }

    private fun scanUrl(url: URL) = scanFiles(Vfs.fromURL(url).use(VfsDir::files).toList())

    private fun scanFiles(files: List<VfsFile>) = files.forEach { file -> scanFile(file) }

    private fun scanFile(file: VfsFile) {
        val fqn = file.relativePath!!.replace('/', '.')
        // todo: filter belli bir forma match etsin, o da olur bu da olur olmaz. String değil belli bir tipi olsun
        if (filter.test(file.relativePath!!) || filter.test(fqn)) {
            // todo: bu classObject neden var? Performans ise başka şekilde halledilsin. Kodu kirletmesin.
            var classObject: ClassAdapter? = null
            scanners.forEach { scanner ->
                try {
                    if (scanner.acceptsInput(file.relativePath!!) || scanner.acceptsInput(fqn)) {
                        classObject = scanner.scan(file, classObject)
                    }
                } catch (e: Exception) {
                    logWarn("could not scan file {} in with scanner {}",
                            file.relativePath,
                            scanner.javaClass.simpleName,
                            e)
                }
            }
        }
    }

    /**
     * serialize to a given directory and file using given serializer
     *
     * * it is preferred to specify a designated directory (for example META-INF/reflections),
     * so that it could be found later much faster using the load method
     */
    fun save(file: File, serializer: Serializer = XmlSerializer) = serializer.save(this, file).also {
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
    fun getTypesAnnotatedWith(annotation: Class<out Annotation>, honorInherited: Boolean): Set<Class<*>> {
        val annotated = ask<TypeAnnotationsScanner, Datum> { values(annotation.fullName()) }
        val classes = getAllAnnotated(annotated, annotation.isAnnotationPresent(Inherited::class.java), honorInherited)
        return (annotated + classes).mapNotNull { classForName(it.value, classLoaders) }.toSet()
    }

    /**
     * get types annotated with a given annotation, both classes and annotations, including annotation member values matching
     *
     * [java.lang.annotation.Inherited] is honored according to given honorInherited
     *
     * depends on TypeAnnotationsScanner configured
     */
    fun getTypesAnnotatedWith(annotation: Annotation, honorInherited: Boolean): Set<Class<*>> {
        val annotated = ask<TypeAnnotationsScanner, Datum> { values(annotation.annotationClass.java.fullName()) }
        val filter =
                annotated.mapNotNull { classForName(it.value, classLoaders) }.filter { withAnnotation(it, annotation) }
                    .toSet()
        val classes =
                getAllAnnotated(filter.map { it.fullName() },
                                annotation.annotationType().isAnnotationPresent(Inherited::class.java),
                                honorInherited)
        return filter + classes.filter { it !in annotated }.mapNotNull {
            classForName(it.value, classLoaders)
        }
    }

    private fun getAllAnnotated(annotated: Collection<Datum>,
                                inherited: Boolean,
                                honorInherited: Boolean): Collection<Datum> {
        return when {
            !honorInherited -> {
                val keys = ask<TypeAnnotationsScanner, Datum> { recursiveValuesIncludingSelf(annotated) }
                ask<SubTypesScanner, Datum> { recursiveValuesIncludingSelf(keys) }
            }
            inherited       -> {
                val keys = annotated.filter { classForName(it.value, classLoaders)?.isInterface == false }
                val subTypes = ask<SubTypesScanner, Datum> { values(keys) }
                ask<SubTypesScanner, Datum> { recursiveValuesIncludingSelf(subTypes) }
            }
            else            -> annotated
        }
    }

    fun allTypes() = ask<SubTypesScanner, String> { allTypes() }

    fun <T> subTypesOf(type: Class<T>) = ask<SubTypesScanner, Class<out T>> { subTypesOf(type) }

    fun constructorsAnnotatedWith(annotation: Annotation) =
            ask<MethodAnnotationsScanner, Constructor<*>> { constructorsAnnotatedWith(annotation) }

    fun constructorsAnnotatedWith(annotation: Class<out Annotation>) =
            ask<MethodAnnotationsScanner, Constructor<*>> { constructorsAnnotatedWith(annotation) }

    fun methodsAnnotatedWith(annotation: Annotation) =
            ask<MethodAnnotationsScanner, Method> { methodsAnnotatedWith(annotation) }

    fun methodsAnnotatedWith(annotation: Class<out Annotation>) =
            ask<MethodAnnotationsScanner, Method> { methodsAnnotatedWith(annotation) }

    fun methodsMatchParams(vararg types: Class<*>) = ask<MethodParameterScanner, Method> { methodsMatchParams(*types) }

    fun methodsReturn(returnType: Class<*>) = ask<MethodParameterScanner, Method> { methodsReturn(returnType) }

    fun methodsWithAnyParamAnnotated(annotation: Annotation) =
            ask<MethodParameterScanner, Method> { methodsWithAnyParamAnnotated(annotation) }

    fun methodsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            ask<MethodParameterScanner, Method> { methodsWithAnyParamAnnotated(annotation) }

    fun constructorsMatchParams(vararg types: Class<*>) =
            ask<MethodParameterScanner, Constructor<*>> { constructorsMatchParams(*types) }

    fun constructorsWithAnyParamAnnotated(annotation: Annotation) =
            ask<MethodParameterScanner, Constructor<*>> { constructorsWithAnyParamAnnotated(annotation) }

    fun constructorsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            ask<MethodParameterScanner, Constructor<*>> { constructorsWithAnyParamAnnotated(annotation) }

    fun fieldsAnnotatedWith(annotation: Annotation) =
            ask<FieldAnnotationsScanner, Field> { fieldsAnnotatedWith(annotation) }

    fun fieldsAnnotatedWith(annotation: Class<out Annotation>) =
            ask<FieldAnnotationsScanner, Field> { fieldsAnnotatedWith(annotation) }

    fun resources(pattern: Pattern) = ask<ResourcesScanner, Datum> { resources(pattern) }

    fun paramNames(executable: Executable) = ask<MethodParameterNamesScanner, String> { paramNames(executable) }

    fun usages(o: AccessibleObject) = ask<MemberUsageScanner, Member> { usages(o) }

    inline fun <reified S : Scanner, R> ask(flatMap: S.() -> Iterable<R>): Set<R> {
        return scanners.filterIsInstance<S>().apply {
            if (isEmpty()) throw RuntimeException("Scanner ${S::class.java.simpleName} was not configured")
        }.flatMap(flatMap).toSet()
    }
}

fun List<Configuration>.merged() = Configuration(scanners = flatMap { it.scanners }.toSet())

/**
 * collect saved Reflections resources from all urls that contains the given packagePrefix and matches the given resourceNameFilter
 * and de-serializes them using the default serializer [org.reflections.serializers.XmlSerializer] or using the optionally supplied serializer
 *
 * by default, resources are collected from all urls that contains the package META-INF/reflections
 * and includes files matching the pattern .*-reflections.xml
 *
 * it is preferred to use a designated resource prefix (for example META-INF/reflections but not just META-INF),
 * so that relevant urls could be found much faster
 *
 * @param serializer - optionally supply one serializer instance. if not specified or null, [org.reflections.serializers.XmlSerializer] will be used
 */
fun merged(configuration: Configuration,
           packagePrefix: String = "META-INF/reflections/",
           resourceNameFilter: Filter = Filter.Include(".*-reflections.xml"),
           serializer: Serializer = XmlSerializer): Configuration {
    val urls = urlForPackage(packagePrefix)
    val startTime = System.currentTimeMillis()
    val others = Vfs.findFiles(urls, packagePrefix, resourceNameFilter).map { file ->
        tryOrThrow("could not merge $file") {
            file.openInputStream().use { stream -> serializer.read(stream) }
        }
    }
    val elapsedTime = System.currentTimeMillis() - startTime
    val merged = (listOf(configuration) + others).merged()
    logInfo("Reflections took {} ms to collect {} urls, producing {} keys and %d values [{}]",
            elapsedTime,
            urls.size,
            merged.scanners.sumBy(Scanner::keyCount),
            merged.scanners.sumBy(Scanner::valueCount),
            urls.joinToString())
    return merged
}

sealed class Filter {
    abstract fun test(s: String): Boolean

    data class Include(val patternString: String) : Filter() {
        private val pattern = Pattern.compile(patternString)
        override fun test(s: String) = pattern.matcher(s).matches()
        override fun toString() = "+$patternString"
    }

    data class Exclude(val patternString: String) : Filter() {
        private val pattern = Pattern.compile(patternString)
        override fun test(s: String) = !pattern.matcher(s).matches()
        override fun toString() = "-$patternString"
    }

    data class Composite(val filters: List<Filter>) : Filter() {
        override fun test(s: String): Boolean {
            var accept = filters.isEmpty() || filters[0] is Exclude
            loop@ for (filter in filters) {
                //skip if this filter won't change
                when {
                    accept -> if (filter is Include) continue@loop
                    else   -> if (filter is Exclude) continue@loop
                }
                accept = filter.test(s)
                //break on first exclusion
                if (!accept && filter is Exclude) break
            }
            return accept
        }

        override fun toString() = filters.joinToString()
    }

    companion object {

        /**
         * Parses a string representation of an include/exclude filter.
         *
         * The given includeExcludeString is a comma separated list of package name segments,
         * each starting with either + or - to indicate include/exclude.
         *
         * For example parsePackages("-java, -javax, -sun, -com.sun") or parse("+com.myn,-com.myn.excluded").
         * Note that "-java" will block "java.foo" but not "javax.foo".
         *
         * The input strings "-java" and "-java." are equivalent.
         */
        fun parsePackages(includeExcludeString: String) = parse(includeExcludeString) {
            (if (!it.endsWith(".")) "$it." else it).toPrefixRegex()
        }

        /**
         * Parses a string representation of an include/exclude filter.
         *
         * The given includeExcludeString is a comma separated list of regexes,
         * each starting with either + or - to indicate include/exclude.
         *
         * For example parsePackages("-java\\..*, -javax\\..*, -sun\\..*, -com\\.sun\\..*")
         * or parse("+com\\.myn\\..*,-com\\.myn\\.excluded\\..*").
         * Note that "-java\\..*" will block "java.foo" but not "javax.foo".
         *
         * See also the more useful [Filter.parsePackages] method.
         */
        fun parse(includeExcludeString: String, transformPattern: (String) -> String = { it }) =
                Composite(includeExcludeString.split(',').dropLastWhile { it.isEmpty() }.map { string ->
                    val trimmed = string.trim { it <= ' ' }
                    val prefix = trimmed[0]
                    val pattern = transformPattern(trimmed.substring(1))
                    when (prefix) {
                        '+'  -> Include(pattern)
                        '-'  -> Exclude(pattern)
                        else -> throw RuntimeException("includeExclude should start with either + or -")
                    }
                })
    }
}

fun String.toPrefixRegex() = replace(".", "\\.") + ".*"
fun Class<*>.toPackageNameRegex() = "${`package`.name}.".toPrefixRegex()