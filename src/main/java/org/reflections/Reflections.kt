package org.reflections

import jdk.nashorn.internal.runtime.regexp.joni.Config.log
import org.reflections.Predicates.`in`
import org.reflections.Predicates.not
import org.reflections.ReflectionUtils.filter
import org.reflections.ReflectionUtils.forName
import org.reflections.ReflectionUtils.forNames
import org.reflections.ReflectionUtils.getSuperTypes
import org.reflections.ReflectionUtils.withAnnotation
import org.reflections.ReflectionUtils.withAnyParameterAnnotation
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
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.reflections.util.Utils.close
import org.reflections.util.Utils.getConstructorsFromDescriptors
import org.reflections.util.Utils.getFieldFromString
import org.reflections.util.Utils.getMembersFromDescriptors
import org.reflections.util.Utils.getMethodsFromDescriptors
import org.reflections.util.Utils.index
import org.reflections.util.Utils.name
import org.reflections.util.Utils.names
import org.reflections.vfs.Vfs
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.String.format
import java.lang.annotation.Inherited
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.net.URL
import java.util.*
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
 * <pre>
 * Reflections reflections = new Reflections("my.project.prefix");
 *
 * Set&#60Class&#60? extends SomeType>> subTypes = reflections.getSubTypesOf(SomeType.class);
 *
 * Set&#60Class&#60?>> annotated = reflections.getTypesAnnotatedWith(SomeAnnotation.class);
</pre> *
 *
 * Basically, to use Reflections first instantiate it with one of the constructors, then depending on the scanners, use the convenient query methods:
 * <pre>
 * Reflections reflections = new Reflections("my.package.prefix");
 * //or
 * Reflections reflections = new Reflections(ClasspathHelper.forPackage("my.package.prefix"),
 * new SubTypesScanner(), new TypesAnnotationScanner(), new FilterBuilder().include(...), ...);
 *
 * //or using the ConfigurationBuilder
 * new Reflections(new ConfigurationBuilder()
 * .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix("my.project.prefix")))
 * .setUrls(ClasspathHelper.forPackage("my.project.prefix"))
 * .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner().filterResultsBy(optionalFilter), ...));
</pre> *
 * And then query, for example:
 * <pre>
 * Set&#60Class&#60? extends Module>> modules = reflections.getSubTypesOf(com.google.inject.Module.class);
 * Set&#60Class&#60?>> singletons =             reflections.getTypesAnnotatedWith(javax.inject.Singleton.class);
 *
 * Set&#60String> properties =       reflections.getResources(Pattern.compile(".*\\.properties"));
 * Set&#60Constructor> injectables = reflections.getConstructorsAnnotatedWith(javax.inject.Inject.class);
 * Set&#60Method> deprecateds =      reflections.getMethodsAnnotatedWith(javax.ws.rs.Path.class);
 * Set&#60Field> ids =               reflections.getFieldsAnnotatedWith(javax.persistence.Id.class);
 *
 * Set&#60Method> someMethods =      reflections.getMethodsMatchParams(long.class, int.class);
 * Set&#60Method> voidMethods =      reflections.getMethodsReturn(void.class);
 * Set&#60Method> pathParamMethods = reflections.getMethodsWithAnyParamAnnotated(PathParam.class);
 * Set&#60Method> floatToString =    reflections.getConverters(Float.class, String.class);
 * List&#60String> parameterNames =  reflections.getMethodsParamNames(Method.class);
 *
 * Set&#60Member> fieldUsage =       reflections.getFieldUsage(Field.class);
 * Set&#60Member> methodUsage =      reflections.getMethodUsage(Method.class);
 * Set&#60Member> constructorUsage = reflections.getConstructorUsage(Constructor.class);
</pre> *
 *
 * You can use other scanners defined in Reflections as well, such as: SubTypesScanner, TypeAnnotationsScanner (both default),
 * ResourcesScanner, MethodAnnotationsScanner, ConstructorAnnotationsScanner, FieldAnnotationsScanner,
 * MethodParameterScanner, MethodParameterNamesScanner, MemberUsageScanner or any custom scanner.
 *
 * Use [.getStore] to access and query the store directly
 *
 * In order to save the store metadata, use [.save] or [.save]
 * for example with [org.reflections.serializers.XmlSerializer] or [org.reflections.serializers.JavaCodeSerializer]
 *
 * In order to collect pre saved metadata and avoid re-scanning, use [.collect]}
 *
 * *Make sure to scan all the transitively relevant packages.
 * <br></br>for instance, given your class C extends B extends A, and both B and A are located in another package than C,
 * when only the package of C is scanned - then querying for sub types of A returns nothing (transitive), but querying for sub types of B returns C (direct).
 * In that case make sure to scan all relevant packages a priori.*
 *
 *
 *
 *
 *
 *For Javadoc, source code, and more information about Reflections Library, see http://github.com/ronmamo/reflections/
 */
class Reflections(@Transient val configuration: Configuration) {

    /**
     * returns the [org.reflections.Store] used for storing and querying the metadata
     */
    val store = Store()

    /**
     * get all types scanned. this is effectively similar to getting all subtypes of Object.
     *
     * depends on SubTypesScanner configured with `SubTypesScanner(false)`, otherwise `ReflectionsException` is thrown
     *
     * *note using this might be a bad practice. it is better to get types matching some criteria,
     * such as [.getSubTypesOf] or [.getTypesAnnotatedWith]*
     *
     * @return Set of String, and not of Class, in order to avoid definition of all types in PermGen
     */
    val allTypes: Set<String>
        get() {
            val allTypes = store.getAll(index(SubTypesScanner::class.java), Any::class.java.name).toSet()
            if (allTypes.isEmpty()) {
                throw ReflectionsException("Couldn't find subtypes of Object. " + "Make sure SubTypesScanner initialized to include Object class - new SubTypesScanner(false)")
            }
            return allTypes
        }

    init {
        if (!configuration.scanners.isEmpty()) {
            //inject to scanners
            for (scanner in configuration.scanners) {
                scanner.configuration = configuration
                scanner.store = store.getOrCreate(index(scanner.javaClass))
            }

            scan()

            if (configuration.shouldExpandSuperTypes()) {
                expandSuperTypes()
            }
        }
    }

    /**
     * a convenient constructor for scanning within a package prefix.
     *
     * this actually create a [org.reflections.Configuration] with:
     * <br></br> - urls that contain resources with name `prefix`
     * <br></br> - filterInputsBy where name starts with the given `prefix`
     * <br></br> - scanners set to the given `scanners`, otherwise defaults to [org.reflections.scanners.TypeAnnotationsScanner] and [org.reflections.scanners.SubTypesScanner].
     *
     * @param prefix   package prefix, to be used with [org.reflections.util.ClasspathHelper.forPackage] )}
     * @param scanners optionally supply scanners, otherwise defaults to [org.reflections.scanners.TypeAnnotationsScanner] and [org.reflections.scanners.SubTypesScanner]
     */
    constructor(prefix: String, vararg scanners: Scanner) : this(prefix as Any, scanners)

    /**
     * a convenient constructor for Reflections, where given `Object...` parameter types can be either:
     *
     *  * [String] - would add urls using [org.reflections.util.ClasspathHelper.forPackage] ()}
     *  * [Class] - would add urls using [org.reflections.util.ClasspathHelper.forClass]
     *  * [ClassLoader] - would use this classloaders in order to find urls in [org.reflections.util.ClasspathHelper.forPackage] and [org.reflections.util.ClasspathHelper.forClass]
     *  * [org.reflections.scanners.Scanner] - would use given scanner, overriding the default scanners
     *  * [java.net.URL] - would add the given url for scanning
     *  * [Object[]] - would use each element as above
     *
     *
     *
     * use any parameter type in any order. this constructor uses instanceof on each param and instantiate a [org.reflections.util.ConfigurationBuilder] appropriately.
     * if you prefer the usual statically typed constructor, don't use this, although it can be very useful.
     *
     * <br></br><br></br>for example:
     * <pre>
     * new Reflections("my.package", classLoader);
     * //or
     * new Reflections("my.package", someScanner, anotherScanner, classLoader);
     * //or
     * new Reflections(myUrl, myOtherUrl);
    </pre> *
     */
    constructor(vararg params: Any) : this(ConfigurationBuilder.build(*params))

    private constructor() : this(ConfigurationBuilder())

    private fun scan() {
        if (configuration.urls.isEmpty()) {
            logWarn("given scan urls are empty. set urls in the configuration")
            return
        }

        logDebug("going to scan these urls:\n{}", configuration.urls.joinToString("\n"))

        var time = System.currentTimeMillis()
        var scannedUrls = 0
        val executorService = configuration.executorService
        val futures = mutableListOf<Future<*>>()

        for (url in configuration.urls) {
            try {
                if (executorService != null) {
                    futures.add(executorService.submit {
                        logDebug("[{}] scanning {}", Thread.currentThread(), url)
                        scan(url)
                    })
                } else {
                    scan(url)
                }
                scannedUrls++
            } catch (e: ReflectionsException) {
                logWarn("could not create Vfs.Dir from url. ignoring the exception and continuing", e)
            }
        }

        //todo use CompletionService
        futures.forEach { future ->
            try {
                future.get()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        time = System.currentTimeMillis() - time

        //gracefully shutdown the parallel scanner executor service.
        executorService?.shutdown()

        if (executorService is ThreadPoolExecutor) {
            logInfo("Reflections took {} ms to scan {} urls, producing {} keys and {} values {}",
                    time,
                    scannedUrls,
                    keyCount(),
                    valueCount(),
                    format("[using %d cores]", executorService.maximumPoolSize))
        } else {
            logInfo("Reflections took {} ms to scan {} urls, producing {} keys and {} values {}",
                    time,
                    scannedUrls,
                    keyCount(),
                    valueCount(),
                    "")
        }
    }

    private fun keyCount(): Int = store.keySet().sumBy { store[it].keySet().size }
    private fun valueCount(): Int = store.keySet().sumBy { store[it].size() }

    private fun scan(url: URL) {
        val dir = Vfs.fromURL(url)

        try {
            for (file in dir.files) {
                // scan if inputs filter accepts file relative path or fqn
                val inputsFilter = configuration.inputsFilter
                val path = file.relativePath
                val fqn = path!!.replace('/', '.')
                if (inputsFilter == null || inputsFilter(path) || inputsFilter(fqn)) {
                    var classObject: ClassWrapper? = null
                    for (scanner in configuration.scanners) {
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
        } finally {
            dir.close()
        }
    }

    /**
     * merges saved Reflections resources from the given input stream, using the serializer configured in this instance's Configuration
     * <br></br> useful if you know the serialized resource location and prefer not to look it up the classpath
     */
    private fun collect(inputStream: InputStream): Reflections {
        try {
            merge(configuration.serializer.read(inputStream))
            logInfo("Reflections collected metadata from input stream using serializer " + configuration.serializer.javaClass.name)
        } catch (ex: Exception) {
            throw ReflectionsException("could not merge input stream", ex)
        }

        return this
    }

    /**
     * merges saved Reflections resources from the given file, using the serializer configured in this instance's Configuration
     *
     *  useful if you know the serialized resource location and prefer not to look it up the classpath
     */
    fun collect(file: File): Reflections {
        var inputStream: FileInputStream? = null
        try {
            inputStream = FileInputStream(file)
            return collect(inputStream)
        } catch (e: FileNotFoundException) {
            throw ReflectionsException("could not obtain input stream from file $file", e)
        } finally {
            close(inputStream)
        }
    }

    /**
     * merges a Reflections instance metadata into this instance
     */
    fun merge(reflections: Reflections): Reflections {
        for (indexName in reflections.store.keySet()) {
            val index = reflections.store[indexName]
            for (key in index.keySet()) {
                for (string in index.get(key)!!) {
                    store.getOrCreate(indexName).put(key, string)
                }
            }
        }
        return this
    }

    /**
     * expand super types after scanning, for super types that were not scanned.
     * this is helpful in finding the transitive closure without scanning all 3rd party dependencies.
     * it uses [ReflectionUtils.getSuperTypes].
     *
     *
     * for example, for classes A,B,C where A supertype of B, B supertype of C:
     *
     *  * if scanning C resulted in B (B->C in store), but A was not scanned (although A supertype of B) - then getSubTypes(A) will not return C
     *  * if expanding supertypes, B will be expanded with A (A->B in store) - then getSubTypes(A) will return C
     *
     */
    fun expandSuperTypes() {
        if (store.keySet().contains(index(SubTypesScanner::class.java))) {
            val mmap = store[index(SubTypesScanner::class.java)]
            val keys = mmap.keySet() - mmap.values().toSet()
            val expand = Multimap<String, String>()
            for (key in keys) {
                val type = ReflectionUtils.forName(key, *loaders())
                if (type != null) {
                    expandSupertypes(expand, key, type)
                }
            }
            mmap.putAll(expand)
        }
    }

    //query

    /**
     * gets all sub types in hierarchy of a given type
     *
     * depends on SubTypesScanner configured
     */
    fun <T> getSubTypesOf(type: Class<T>): Set<Class<out T>> {
        return ReflectionUtils.forNames<T>(store.getAll(index(SubTypesScanner::class.java), listOf(type.name)),
                                           *loaders()).toSet()
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
    @JvmOverloads
    fun getTypesAnnotatedWith(annotation: Class<out Annotation>, honorInherited: Boolean = false): Set<Class<*>> {
        val annotated = store[index(TypeAnnotationsScanner::class.java), annotation.name]
        val classes = getAllAnnotated(annotated, annotation.isAnnotationPresent(Inherited::class.java), honorInherited)
        return ReflectionUtils.forNames<Any>(annotated, *loaders()).toSet() + ReflectionUtils.forNames(classes,
                                                                                                       *loaders())
    }

    /**
     * get types annotated with a given annotation, both classes and annotations, including annotation member values matching
     *
     * [java.lang.annotation.Inherited] is honored according to given honorInherited
     *
     * depends on TypeAnnotationsScanner configured
     */
    @JvmOverloads
    fun getTypesAnnotatedWith(annotation: Annotation, honorInherited: Boolean = false): Set<Class<*>> {
        val annotated = store[index(TypeAnnotationsScanner::class.java), annotation.annotationClass.java.name]
        val filter =
                ReflectionUtils.filter(ReflectionUtils.forNames<Any>(annotated, *loaders()),
                                       ReflectionUtils.withAnnotation(annotation))
        val classes =
                getAllAnnotated(names(filter),
                                annotation.annotationType().isAnnotationPresent(Inherited::class.java),
                                honorInherited)
        return filter + forNames(filter(classes, not(`in`(setOf(annotated)))), *loaders())
    }

    private fun getAllAnnotated(annotated: Iterable<String>,
                                inherited: Boolean,
                                honorInherited: Boolean): Iterable<String> {
        return if (honorInherited) {
            if (inherited) {
                val subTypes = store[index(SubTypesScanner::class.java), filter(annotated, { input: String ->
                    val type = forName(input, *loaders())
                    type != null && !type.isInterface
                })]
                subTypes + store.getAll(index(SubTypesScanner::class.java), subTypes)
            } else {
                annotated
            }
        } else {
            val subTypes = annotated + store.getAll(index(TypeAnnotationsScanner::class.java), annotated)
            subTypes + store.getAll(index(SubTypesScanner::class.java), subTypes)
        }
    }

    /**
     * get all methods annotated with a given annotation
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun getMethodsAnnotatedWith(annotation: Class<out Annotation>): Set<Method> {
        val methods = store[index(MethodAnnotationsScanner::class.java), annotation.name]
        return getMethodsFromDescriptors(methods, *loaders())
    }

    /**
     * get all methods annotated with a given annotation, including annotation member values matching
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun getMethodsAnnotatedWith(annotation: Annotation): Set<Method> {
        return filter(getMethodsAnnotatedWith(annotation.annotationClass.java), withAnnotation(annotation))
    }

    /**
     * get methods with parameter types matching given `types`
     */
    fun getMethodsMatchParams(vararg types: Class<*>): Set<Method> {
        return getMethodsFromDescriptors(store[index(MethodParameterScanner::class.java), names(*types).toString()],
                                         *loaders())
    }

    /**
     * get methods with return type match given type
     */
    fun getMethodsReturn(returnType: Class<*>): Set<Method> {
        return getMethodsFromDescriptors(store[index(MethodParameterScanner::class.java), names(returnType)],
                                         *loaders())
    }

    /**
     * get methods with any parameter annotated with given annotation
     */
    fun getMethodsWithAnyParamAnnotated(annotation: Class<out Annotation>): Set<Method> {
        return getMethodsFromDescriptors(store[index(MethodParameterScanner::class.java), annotation.name],
                                         *loaders())

    }

    /**
     * get methods with any parameter annotated with given annotation, including annotation member values matching
     */
    fun getMethodsWithAnyParamAnnotated(annotation: Annotation): Set<Method> {
        return filter(getMethodsWithAnyParamAnnotated(annotation.annotationClass.java),
                      withAnyParameterAnnotation(annotation))
    }

    /**
     * get all constructors annotated with a given annotation
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun getConstructorsAnnotatedWith(annotation: Class<out Annotation>): Set<Constructor<*>> {
        val methods = store[index(MethodAnnotationsScanner::class.java), annotation.name]
        return getConstructorsFromDescriptors(methods, *loaders())
    }

    /**
     * get all constructors annotated with a given annotation, including annotation member values matching
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun getConstructorsAnnotatedWith(annotation: Annotation): Set<Constructor<*>> {
        return filter(getConstructorsAnnotatedWith(annotation.annotationType()), withAnnotation(annotation))
    }

    /**
     * get constructors with parameter types matching given `types`
     */
    fun getConstructorsMatchParams(vararg types: Class<*>): Set<Constructor<*>> {
        return getConstructorsFromDescriptors(store[index(MethodParameterScanner::class.java), names(*types).toString()],
                                              *loaders())
    }

    /**
     * get constructors with any parameter annotated with given annotation
     */
    fun getConstructorsWithAnyParamAnnotated(annotation: Class<out Annotation>): Set<Constructor<*>> {
        return getConstructorsFromDescriptors(store[index(MethodParameterScanner::class.java), annotation.name],
                                              *loaders())
    }

    /**
     * get constructors with any parameter annotated with given annotation, including annotation member values matching
     */
    fun getConstructorsWithAnyParamAnnotated(annotation: Annotation): Set<Constructor<*>> {
        return filter(getConstructorsWithAnyParamAnnotated(annotation.annotationType()),
                      withAnyParameterAnnotation(annotation))
    }

    /**
     * get all fields annotated with a given annotation
     *
     * depends on FieldAnnotationsScanner configured
     */
    fun getFieldsAnnotatedWith(annotation: Class<out Annotation>): Set<Field> {
        val result = mutableSetOf<Field>()
        for (annotated in store[index(FieldAnnotationsScanner::class.java), annotation.name]) {
            result.add(getFieldFromString(annotated, *loaders()))
        }
        return result
    }

    /**
     * get all methods annotated with a given annotation, including annotation member values matching
     *
     * depends on FieldAnnotationsScanner configured
     */
    fun getFieldsAnnotatedWith(annotation: Annotation): Set<Field> {
        return filter(getFieldsAnnotatedWith(annotation.annotationType()), withAnnotation(annotation))
    }

    /**
     * get resources relative paths where simple name (key) matches given namePredicate
     *
     * depends on ResourcesScanner configured
     */
    private fun getResources(namePredicate: (String) -> Boolean): Set<String> {
        val resources = store[index(ResourcesScanner::class.java)].keySet().filter(namePredicate)
        return store[index(ResourcesScanner::class.java), resources].toMutableSet()
    }

    /**
     * get resources relative paths where simple name (key) matches given regular expression
     *
     * depends on ResourcesScanner configured
     * <pre>Set&lt;String> xmls = reflections.getResources(".*\\.xml");</pre>
     */
    fun getResources(pattern: Pattern): Set<String> {
        return getResources { input: String -> pattern.matcher(input).matches() }
    }

    /**
     * get parameter names of given `method`
     *
     * depends on MethodParameterNamesScanner configured
     */
    fun getMethodParamNames(method: Method): List<String> {
        val names = store[index(MethodParameterNamesScanner::class.java), name(method)].toList()
        return if (names.isEmpty()) emptyList()
        else Arrays.asList(*names.single().split(", ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    }

    /**
     * get parameter names of given `constructor`
     *
     * depends on MethodParameterNamesScanner configured
     */
    fun getConstructorParamNames(constructor: Constructor<*>): List<String> {
        val names = store[index(MethodParameterNamesScanner::class.java), name(constructor)].toList()
        return if (names.isEmpty()) emptyList()
        else Arrays.asList(*names.single().split(", ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    }

    /**
     * get all given `field` usages in methods and constructors
     *
     * depends on MemberUsageScanner configured
     */
    fun getFieldUsage(field: Field): Set<Member> {
        return getMembersFromDescriptors(store[index(MemberUsageScanner::class.java), name(field)])
    }

    /**
     * get all given `method` usages in methods and constructors
     *
     * depends on MemberUsageScanner configured
     */
    fun getMethodUsage(method: Method): Set<Member> {
        return getMembersFromDescriptors(store[index(MemberUsageScanner::class.java), name(method)])
    }

    /**
     * get all given `constructors` usages in methods and constructors
     *
     * depends on MemberUsageScanner configured
     */
    fun getConstructorUsage(constructor: Constructor<*>): Set<Member> {
        return getMembersFromDescriptors(store[index(MemberUsageScanner::class.java), name(constructor)])
    }

    /**
     * serialize to a given directory and filename using given serializer
     *
     * * it is preferred to specify a designated directory (for example META-INF/reflections),
     * so that it could be found later much faster using the load method
     */
    @JvmOverloads
    fun save(filename: String, serializer: Serializer = configuration.serializer): File {
        val file = serializer.save(this, filename)
        logInfo("Reflections successfully saved in " + file.absolutePath + " using " + serializer.javaClass.simpleName)
        return file
    }

    private fun loaders(): Array<out ClassLoader> {
        return configuration.classLoaders
    }

    companion object {

        /**
         * collect saved Reflection xml resources and merge it into a Reflections instance
         *
         * by default, resources are collected from all urls that contains the package META-INF/reflections
         * and includes files matching the pattern .*-reflections.xml
         */
        fun collect(): Reflections? {
            val filter = FilterBuilder().include(".*-reflections.xml")
            return collect("META-INF/reflections/", filter::test)
        }

        /**
         * collect saved Reflections resources from all urls that contains the given packagePrefix and matches the given resourceNameFilter
         * and de-serializes them using the default serializer [org.reflections.serializers.XmlSerializer] or using the optionally supplied optionalSerializer
         *
         *
         * it is preferred to use a designated resource prefix (for example META-INF/reflections but not just META-INF),
         * so that relevant urls could be found much faster
         *
         * @param optionalSerializer - optionally supply one serializer instance. if not specified or null, [org.reflections.serializers.XmlSerializer] will be used
         */
        fun collect(packagePrefix: String,
                    resourceNameFilter: (String) -> Boolean,
                    vararg optionalSerializer: Serializer): Reflections? {
            val serializer = optionalSerializer.singleOrNull() ?: XmlSerializer()
            val urls = ClasspathHelper.forPackage(packagePrefix)
            if (urls.isEmpty()) {
                return null
            }
            val start = System.currentTimeMillis()
            val reflections = Reflections()
            val files = Vfs.findFiles(urls, packagePrefix, resourceNameFilter)
            for (file in files) {
                var inputStream: InputStream? = null
                try {
                    inputStream = file.openInputStream()
                    reflections.merge(serializer.read(inputStream))
                } catch (e: IOException) {
                    throw ReflectionsException("could not merge $file", e)
                } finally {
                    close(inputStream)
                }
            }

            if (log != null) {
                val store = reflections.store
                var keys = 0
                var values = 0
                for (index in store.keySet()) {
                    keys += store[index].keySet().size
                    values += store[index].size()
                }

                if (urls.size > 1) {
                    logInfo(format("Reflections took %d ms to collect %d url%s, producing %d keys and %d values [%s]",
                                   System.currentTimeMillis() - start,
                                   urls.size,
                                   "s",
                                   keys,
                                   values,
                                   urls.joinToString()))
                } else {
                    logInfo(format("Reflections took %d ms to collect %d url%s, producing %d keys and %d values [%s]",
                                   System.currentTimeMillis() - start,
                                   urls.size,
                                   "",
                                   keys,
                                   values,
                                   urls.joinToString()))
                }
            }
            return reflections
        }

        private fun expandSupertypes(mmap: Multimap<in String, in String>, key: String, type: Class<*>) {
            for (supertype in getSuperTypes(type)) {
                if (mmap.put(supertype.name, key)) {
                    logDebug("expanded subtype {} -> {}", supertype.name, key)
                    expandSupertypes(mmap, supertype.name, supertype)
                }
            }
        }
    }
}
