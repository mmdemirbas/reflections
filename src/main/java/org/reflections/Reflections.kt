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
import org.reflections.scanners.getOrThrow
import org.reflections.scanners.getOrThrowRecursively
import org.reflections.scanners.getOrThrowRecursivelyExceptSelf
import org.reflections.scanners.keyCount
import org.reflections.scanners.valueCount
import org.reflections.serializers.Serializer
import org.reflections.serializers.XmlSerializer
import org.reflections.util.IndexKey
import org.reflections.util.Multimap
import org.reflections.util.annotationType
import org.reflections.util.classForName
import org.reflections.util.directParentsExceptObject
import org.reflections.util.fullName
import org.reflections.util.logDebug
import org.reflections.util.logInfo
import org.reflections.util.logWarn
import org.reflections.util.tryOrThrow
import org.reflections.util.urlForPackage
import org.reflections.util.withAnnotation
import org.reflections.util.withAnyParameterAnnotation
import org.reflections.vfs.Vfs
import java.io.File
import java.lang.annotation.Inherited
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
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
 * Set&#60Class&#60? extends SomeType>> subTypes = reflections.getSubTypesOf(SomeType.class);
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
 * List&#60String> parameters =  reflections.getMethodsParamNames(Method.class);
 *
 * Set&#60Member> fieldUsage =       reflections.getUsage(Field.class);
 * Set&#60Member> methodUsage =      reflections.getUsage(Method.class);
 * Set&#60Member> constructorUsage = reflections.getUsage(Constructor.class);
 * ```
 *
 * You can use other scanners defined in Reflections as well, such as: SubTypesScanner, TypeAnnotationsScanner (both default),
 * ResourcesScanner, MethodAnnotationsScanner, ConstructorAnnotationsScanner, FieldAnnotationsScanner,
 * MethodParameterScanner, MethodParameterNamesScanner, MemberUsageScanner or any custom scanner.
 *
 * Use [getStore] to access and query the store directly
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
    val stores = configuration.scanners

    init {
        configuration.scanners.forEach { it.configuration = configuration }
        scan()
        if (configuration.expandSuperTypes) expandSuperTypes()
    }

    /**
     * get all types scanned. this is effectively similar to getting all subtypes of Object.
     *
     * depends on SubTypesScanner configured with `SubTypesScanner(false)`, otherwise `RuntimeException` is thrown
     *
     * *note using this might be a bad practice. it is better to get types matching some criteria,
     * such as [getSubTypesOf] or [getTypesAnnotatedWith]*
     *
     * @return Set of String, and not of Class, in order to avoid definition of all types in PermGen
     */
    val allTypes by lazy {
        val allTypes =
                stores.getOrThrowRecursivelyExceptSelf<SubTypesScanner>(keys = listOf(Any::class.java.fullName()))
                    .toSet()
        when {
            allTypes.isEmpty() -> throw RuntimeException("Couldn't find subtypes of Object. Make sure SubTypesScanner initialized to include Object class - new SubTypesScanner(false)")
            else               -> allTypes.map { it.value }.toSet()
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

        var time = System.currentTimeMillis()
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

        time = System.currentTimeMillis() - time

        //gracefully shutdown the parallel scanner executor service.
        executorService?.shutdown()

        logInfo("Reflections took {} ms to scan {} urls, producing {} keys and {} values {}",
                time,
                scannedUrls,
                stores.keyCount(),
                stores.valueCount(),
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
                configuration.scanners.forEach { scanner ->
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
                merged.stores.keyCount(),
                merged.stores.valueCount(),
                urls.joinToString())
        return merged
    }

    fun merged(others: List<Reflections>) =
            Reflections(Configuration(scanners = (listOf(this) + others).flatMap { it.stores }.toSet()))

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
        stores.filterIsInstance<SubTypesScanner>().forEach {
            val multimap = it.store
            val expand = Multimap<IndexKey, IndexKey>()
            (multimap.keys() - multimap.values()).forEach { key ->
                val type = classForName(key)
                if (type != null) expandSupertypes(expand, key, type)
            }
            multimap.putAll(expand)
        }
    }

    private fun expandSupertypes(mmap: Multimap<in IndexKey, in IndexKey>, key: IndexKey, type: Class<*>): Unit =
            type.directParentsExceptObject().forEach { supertype ->
                if (mmap.put(supertype.fullName(), key)) {
                    logDebug("expanded subtype {} -> {}", supertype.name, key)
                    expandSupertypes(mmap, supertype.fullName(), supertype)
                }
            }


    //query

    /**
     * gets all sub types in hierarchy of a given type
     *
     * depends on SubTypesScanner configured
     */
    fun <T> getSubTypesOf(type: Class<T>) =
            classesForNames<T>(stores.getOrThrowRecursivelyExceptSelf<SubTypesScanner>(listOf(type.fullName()))).toSet()

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
        val annotated = stores.getOrThrow<TypeAnnotationsScanner>(annotation.fullName())
        val classes = getAllAnnotated(annotated, annotation.isAnnotationPresent(Inherited::class.java), honorInherited)
        return classesForNames<Any>((annotated + classes).toSet()).toSet()
    }

    /**
     * get types annotated with a given annotation, both classes and annotations, including annotation member values matching
     *
     * [java.lang.annotation.Inherited] is honored according to given honorInherited
     *
     * depends on TypeAnnotationsScanner configured
     */
    fun getTypesAnnotatedWith(annotation: Annotation, honorInherited: Boolean = false): Set<Class<*>> {
        val annotated = stores.getOrThrow<TypeAnnotationsScanner>(annotation.annotationClass.java.fullName())
        val filter = classesForNames<Any>(annotated).filter { withAnnotation(it, annotation) }.toSet()
        val classes =
                getAllAnnotated(filter.map { it.fullName() },
                                annotation.annotationType().isAnnotationPresent(Inherited::class.java),
                                honorInherited)
        return filter + classesForNames(classes.filter { it !in annotated }.toSet())
    }

    private fun getAllAnnotated(annotated: Collection<IndexKey>,
                                inherited: Boolean,
                                honorInherited: Boolean): Collection<IndexKey> {
        return when {
            !honorInherited -> stores.getOrThrowRecursively<SubTypesScanner>(stores.getOrThrowRecursively<TypeAnnotationsScanner>(
                    annotated))
            inherited       -> {
                val subTypes = stores.getOrThrow<SubTypesScanner>(annotated.filter { input ->
                    classForName(input)?.isInterface == false
                }.toSet())
                stores.getOrThrowRecursively<SubTypesScanner>(subTypes)
            }
            else            -> annotated
        }
    }

    /**
     * get all constructors annotated with a given annotation
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun getConstructorsAnnotatedWith(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodAnnotations().filterIsInstance<Constructor<*>>().toSet()

    /**
     * get all methods annotated with a given annotation, including annotation member values matching
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun getMethodsAnnotatedWith(annotation: Annotation) =
            listOf(annotation.annotationClass.java.fullName()).methodAnnotations().filterIsInstance<Method>().filter {
                withAnnotation(it, annotation)
            }.toSet()

    /**
     * get all methods annotated with a given annotation
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun getMethodsAnnotatedWith(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodAnnotations().filterIsInstance<Method>().toSet()

    /**
     * get methods with parameter types matching given `types`
     */
    fun getMethodsMatchParams(vararg types: Class<*>) =
            types.map { it.fullName() }.methodParams().filterIsInstance<Method>().toSet()

    /**
     * get methods with return type match given type
     */
    fun getMethodsReturn(returnType: Class<*>) =
            listOf(returnType.fullName()).methodParams().filterIsInstance<Method>().toSet()

    /**
     * get methods with any parameter annotated with given annotation
     */
    fun getMethodsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodParams().filterIsInstance<Method>().toSet()

    /**
     * get constructors with parameter types matching given `types`
     */
    fun getConstructorsMatchParams(vararg types: Class<*>) =
            types.map { it.fullName() }.methodParams().filterIsInstance<Constructor<*>>().toSet()

    /**
     * get constructors with any parameter annotated with given annotation
     */
    fun getConstructorsWithAnyParamAnnotated(annotation: Class<out Annotation>) =
            listOf(annotation.fullName()).methodParams().filterIsInstance<Constructor<*>>().toSet()

    fun List<IndexKey>.methodAnnotations() = membersIn<MethodAnnotationsScanner>()
    fun List<IndexKey>.methodParams() = membersIn<MethodParameterScanner>()

    inline fun <reified T : Scanner> List<IndexKey>.membersIn() = stores.getOrThrow<T>(this).map { value ->
        tryOrThrow("Can't resolve member named $value") { value.descriptorToMember() }
    }

    fun IndexKey.descriptorToMember(): Member {
        val descriptor = value
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

    /**
     * get methods with any parameter annotated with given annotation, including annotation member values matching
     */
    fun getMethodsWithAnyParamAnnotated(annotation: Annotation) =
            getMethodsWithAnyParamAnnotated(annotation.annotationClass.java).filter {
                withAnyParameterAnnotation(it, annotation)
            }.toSet()

    /**
     * get all constructors annotated with a given annotation, including annotation member values matching
     *
     * depends on MethodAnnotationsScanner configured
     */
    fun getConstructorsAnnotatedWith(annotation: Annotation) =
            getConstructorsAnnotatedWith(annotation.annotationType()).filter {
                withAnnotation(it, annotation)
            }.toSet()

    /**
     * get constructors with any parameter annotated with given annotation, including annotation member values matching
     */
    fun getConstructorsWithAnyParamAnnotated(annotation: Annotation) =
            getConstructorsWithAnyParamAnnotated(annotation.annotationType()).filter {
                withAnyParameterAnnotation(it, annotation)
            }.toSet()

    /**
     * get all fields annotated with a given annotation
     *
     * depends on FieldAnnotationsScanner configured
     */
    fun getFieldsAnnotatedWith(annotation: Class<out Annotation>) =
            stores.getOrThrow<FieldAnnotationsScanner>(annotation.fullName()).map {
                val field = it.value
                val className = field.substringBeforeLast('.')
                val fieldName = field.substringAfterLast('.')
                tryOrThrow("Can't resolve field named $fieldName") {
                    classForName(className)!!.getDeclaredField(fieldName)
                }
            }.toSet()

    /**
     * get all methods annotated with a given annotation, including annotation member values matching
     *
     * depends on FieldAnnotationsScanner configured
     */
    fun getFieldsAnnotatedWith(annotation: Annotation) = getFieldsAnnotatedWith(annotation.annotationType()).filter {
        withAnnotation(it, annotation)
    }.toSet()

    /**
     * get resources relative paths where simple name (key) matches given namePredicate
     *
     * depends on ResourcesScanner configured
     */
    private fun getResources(namePredicate: (String) -> Boolean) =
            stores.getOrThrow<ResourcesScanner>(stores.getOrThrow<ResourcesScanner>().flatMap { it.store.keys() }.filter {
                namePredicate(it.value)
            }).toMutableSet()

    /**
     * get resources relative paths where simple name (key) matches given regular expression
     *
     * depends on ResourcesScanner configured
     * ```Set<String> xmls = reflections.getResources(".*\\.xml");```
     */
    fun getResources(pattern: Pattern) = getResources { pattern.matcher(it).matches() }

    /**
     * get parameter names of given `method` or `constructor`
     *
     * depends on MethodParameterNamesScanner configured
     */
    fun getParamNames(executable: Executable): List<String> {
        val names = stores.getOrThrow<MethodParameterNamesScanner>(executable.fullName()).toList()
        return when {
            names.isEmpty() -> emptyList()
            else            -> names.single().value.split(", ").dropLastWhile { it.isEmpty() }
        }
    }

    /**
     * get all given `constructor`, `method`, or `field` usages in methods and constructors
     *
     * depends on MemberUsageScanner configured
     */
    fun getUsage(o: AccessibleObject) = listOf(o.fullName()).membersIn<MemberUsageScanner>().toSet()

    fun <T> classesForNames(classes: Iterable<IndexKey>) = classes.mapNotNull { classForName(it) as Class<out T>? }
    fun classForName(typeName: IndexKey) = classForName(typeName.value)
    fun classForName(typeName: String) = classForName(typeName, configuration.classLoaders)
}
