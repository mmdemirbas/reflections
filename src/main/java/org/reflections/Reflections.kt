package org.reflections

import org.reflections.Filter.Include
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
import org.reflections.util.fullName
import org.reflections.util.logDebug
import org.reflections.util.logInfo
import org.reflections.util.logWarn
import org.reflections.util.tryOrThrow
import org.reflections.util.urlForPackage
import org.reflections.util.withAnnotation
import org.reflections.vfs.Vfs
import java.io.File
import java.lang.annotation.Inherited
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.net.URL
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.regex.Pattern

/**
 * Reflections one-stop-shop object
 *
 * Reflections scans your classpath, indexes the metadata, allows you to query it on runtime and may save and collect that information for many modules within your project.
 *
 * Using Reflections you can query your metadata such as:
 *
 *  * get all subtypes of some type
 *  * get all types/constructors/methods/fields annotated with some annotation, optionally with annotation parameters matching
 *  * get all resources matching matching a regular expression
 *  * get all methods with specific signature including parameters, parameter annotations and return type
 *  * get all methods parameter names
 *  * get all fields/methods/constructors usages in code
 *
 *
 * A typical use of Reflections would be:
 * ```
 * Reflections reflections = new Reflections("my.project.prefix");
 *
 * Set&#60Class&#60? extends SomeType>> subTypes = reflections.subTypesOf(SomeType.class);
 *
 * Set&#60Class&#60?>> annotated = reflections.getTypesAnnotatedWith(SomeAnnotation.class);
 * ```
 *
 * Basically, to use Reflections first instantiate it with one of the constructors, then depending on the scanners, use the convenient query methods:
 * ```
 * Reflections reflections = new Reflections("my.package.prefix");
 * //or
 * Reflections reflections = new Reflections(urlForPackage("my.package.prefix"),
 * new SubTypesScanner(), new TypesAnnotationScanner(), new FilterBuilder().include(...), ...);
 *
 * //or using the ConfigurationBuilder
 * new Reflections(new ConfigurationBuilder()
 * .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix("my.project.prefix")))
 * .setUrls(urlForPackage("my.project.prefix"))
 * .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner().filterResultsBy(optionalFilter), ...));
 * ```
 * And then query, for example:
 * ```
 * Set&#60Class&#60? extends Module>> modules = reflections.subTypesOf(com.google.inject.Module.class);
 * Set&#60Class&#60?>> singletons =             reflections.getTypesAnnotatedWith(javax.inject.Singleton.class);
 *
 * Set&#60String> properties =       reflections.resources(Pattern.compile(".*\\.properties"));
 * Set&#60Constructor> injectables = reflections.constructorsAnnotatedWith(javax.inject.Inject.class);
 * Set&#60Method> deprecateds =      reflections.methodsAnnotatedWith(javax.ws.rs.Path.class);
 * Set&#60Field> ids =               reflections.fieldsAnnotatedWith(javax.persistence.Id.class);
 *
 * Set&#60Method> someMethods =      reflections.methodsMatchParams(long.class, int.class);
 * Set&#60Method> voidMethods =      reflections.methodsReturn(void.class);
 * Set&#60Method> pathParamMethods = reflections.methodsWithAnyParamAnnotated(PathParam.class);
 * Set&#60Method> floatToString =    reflections.getConverters(Float.class, String.class);
 * List&#60String> parameters =  reflections.getMethodsParamNames(Method.class);
 *
 * Set&#60Member> fieldUsage =       reflections.usages(Field.class);
 * Set&#60Member> methodUsage =      reflections.usages(Method.class);
 * Set&#60Member> constructorUsage = reflections.usages(Constructor.class);
 * ```
 *
 * You can use other scanners defined in Reflections as well, such as: SubTypesScanner, TypeAnnotationsScanner (both default),
 * ResourcesScanner, MethodAnnotationsScanner, ConstructorAnnotationsScanner, FieldAnnotationsScanner,
 * MethodParameterScanner, MethodParameterNamesScanner, MemberUsageScanner or any custom scanner.
 *
 * Use [ask] to access and query the store directly
 *
 * In order to save the store metadata, use [save] or [save]
 * for example with [org.reflections.serializers.XmlSerializer] or [org.reflections.serializers.JavaCodeSerializer]
 *
 * In order to collect pre saved metadata and avoid re-scanning, use [collect]}
 *
 * *Make sure to scan all the transitively relevant packages.
 * <br></br>for instance, given your class C extends B extends A, and both B and A are located in another package than C,
 * when only the package of C is scanned - then querying for sub types of A returns nothing (transitive), but querying for sub types of B returns C (direct).
 * In that case make sure to scan all relevant packages a priori.*
 *
 *
 *For Javadoc, source code, and more information about Reflections Library, see http://github.com/ronmamo/reflections/
 */
class Reflections(@Transient val configuration: Configuration = Configuration()) {
    inline fun <reified S : Scanner, R> ask(flatMap: S.() -> Iterable<R>) = scanners().filterIsInstance<S>().apply {
        if (isEmpty()) throw RuntimeException("Scanner ${S::class.java.simpleName} was not configured")
    }.flatMap(flatMap).toSet()

    fun keyCount() = scanners().sumBy(Scanner::keyCount)
    fun valueCount() = scanners().sumBy(Scanner::valueCount)
    fun scanners() = configuration.scanners

    init {
        scan()
    }

    /**
     * a convenient constructor for scanning within a package prefix.
     *
     * this actually create a [org.reflections.Configuration] with:
     * <br></br> - urls that contain resources with name `prefix`
     * <br></br> - filterInputsBy where name starts with the given `prefix`
     * <br></br> - scanners set to the given `scanners`, otherwise defaults to [org.reflections.scanners.TypeAnnotationsScanner] and [org.reflections.scanners.SubTypesScanner].
     *
     * @param prefix   package prefix, to be used with [org.reflections.util.urlForPackage] )}
     * @param scanners optionally supply scanners, otherwise defaults to [org.reflections.scanners.TypeAnnotationsScanner] and [org.reflections.scanners.SubTypesScanner]
     */
    constructor(prefix: String, vararg scanners: Scanner) : this(Configuration(filter = Include(prefix.toPrefixRegex()),
                                                                               urls = urlForPackage(prefix),
                                                                               scanners = scanners.toSet()))

    private fun scan() {
        if (configuration.urls.isEmpty()) {
            logWarn("given scan urls are empty. set urls in the configuration")
            return
        }

        logDebug("going to scan these urls:\n{}", configuration.urls.joinToString("\n"))

        val startTime = System.currentTimeMillis()
        var scannedUrls = 0
        val executorService = configuration.executorService
        val futures = mutableListOf<Future<*>>()

        configuration.urls.forEach { url ->
            try {
                when {
                    executorService != null -> futures.add(executorService.submit {
                        logDebug("[{}] scanning {}", Thread.currentThread(), url)
                        scan(url)
                    })
                    else                    -> scan(url)
                }
                scannedUrls++
            } catch (e: RuntimeException) {
                logWarn("could not create VfsDir from url. ignoring the exception and continuing", e)
            }
        }

        //todo use CompletionService
        futures.forEach { it.get() }

        scanners().forEach { scanner -> scanner.afterScan() }

        val elapsedTime = System.currentTimeMillis() - startTime

        //gracefully shutdown the parallel scanner executor service.
        executorService?.shutdown()

        logInfo("Reflections took {} ms to scan {} urls, producing {} keys and {} values {}",
                elapsedTime,
                scannedUrls,
                keyCount(),
                valueCount(),
                if (executorService is ThreadPoolExecutor) "[using ${executorService.maximumPoolSize} cores]" else "")
    }

    private fun scan(url: URL) = Vfs.fromURL(url).use { dir ->
        dir.files.forEach { file ->
            // scan if inputs filter accepts file relative path or fqn
            val inputsFilter = configuration.filter
            val path = file.relativePath
            val fqn = path!!.replace('/', '.')
            if (inputsFilter.test(path) || inputsFilter.test(fqn)) {
                var classObject: ClassAdapter? = null
                scanners().forEach { scanner ->
                    try {
                        if (scanner.acceptsInput(path) || scanner.acceptsInput(fqn)) {
                            classObject = scanner.scan(file, classObject)
                        }
                    } catch (e: Exception) {
                        logWarn("could not scan file {} in url {} with scanner {}",
                                file.relativePath,
                                url.toExternalForm(),
                                scanner.javaClass.simpleName,
                                e)
                    }
                }
            }
        }
    }

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
    fun merged(packagePrefix: String = "META-INF/reflections/",
               resourceNameFilter: Filter = Include(".*-reflections.xml"),
               serializer: Serializer = XmlSerializer): Reflections {
        val urls = urlForPackage(packagePrefix)
        val start = System.currentTimeMillis()
        val others = Vfs.findFiles(urls, packagePrefix, resourceNameFilter).map { file ->
            tryOrThrow("could not merge $file") {
                file.openInputStream().use { stream -> serializer.read(stream) }
            }
        }
        val time = System.currentTimeMillis() - start
        val merged = merged(others)
        logInfo("Reflections took {} ms to collect {} urls, producing {} keys and %d values [{}]",
                time,
                urls.size,
                merged.keyCount(),
                merged.valueCount(),
                urls.joinToString())
        return merged
    }

    private fun merged(others: List<Reflections>) =
            Reflections(Configuration(scanners = (listOf(this) + others).flatMap { it.scanners() }.toSet()))

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
    fun getTypesAnnotatedWith(annotation: Class<out Annotation>, honorInherited: Boolean = false): Set<Class<*>> {
        val annotated = ask<TypeAnnotationsScanner, Datum> { values(annotation.fullName()) }
        val classes = getAllAnnotated(annotated, annotation.isAnnotationPresent(Inherited::class.java), honorInherited)
        return classesForNames<Any>(annotated + classes).toSet()
    }

    /**
     * get types annotated with a given annotation, both classes and annotations, including annotation member values matching
     *
     * [java.lang.annotation.Inherited] is honored according to given honorInherited
     *
     * depends on TypeAnnotationsScanner configured
     */
    fun getTypesAnnotatedWith(annotation: Annotation, honorInherited: Boolean = false): Set<Class<*>> {
        val annotated = ask<TypeAnnotationsScanner, Datum> { values(annotation.annotationClass.java.fullName()) }
        val filter = classesForNames<Any>(annotated).filter { withAnnotation(it, annotation) }.toSet()
        val classes =
                getAllAnnotated(filter.map { it.fullName() },
                                annotation.annotationType().isAnnotationPresent(Inherited::class.java),
                                honorInherited)
        return filter + classesForNames(classes.filter { it !in annotated })
    }

    private fun getAllAnnotated(annotated: Collection<Datum>,
                                inherited: Boolean,
                                honorInherited: Boolean): Collection<Datum> {
        return when {
            !honorInherited -> ask<SubTypesScanner, Datum> {
                recursiveValuesIncludingSelf(ask<TypeAnnotationsScanner, Datum> {
                    recursiveValuesIncludingSelf(annotated)
                })
            }
            inherited       -> {
                val subTypes =
                        ask<SubTypesScanner, Datum> { values(annotated.filter { input -> classForName(input)?.isInterface == false }) }
                ask<SubTypesScanner, Datum> { recursiveValuesIncludingSelf(subTypes) }
            }
            else            -> annotated
        }
    }

    private fun <T> classesForNames(classes: Iterable<Datum>) = classes.mapNotNull { classForName(it) as Class<out T>? }
    private fun classForName(typeName: Datum) = classForName(typeName.value, configuration.classLoaders)


    //query

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
}