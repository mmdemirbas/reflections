package org.reflections.util

import com.google.common.collect.Lists
import com.google.common.collect.ObjectArrays
import com.google.common.collect.Sets
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.reflections.*
import org.reflections.adapters.JavaReflectionAdapter
import org.reflections.adapters.JavassistAdapter
import org.reflections.adapters.MetadataAdapter
import org.reflections.scanners.Scanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.serializers.Serializer
import org.reflections.serializers.XmlSerializer
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Predicate

/**
 * a fluent builder for [org.reflections.Configuration], to be used for constructing a [org.reflections.Reflections] instance
 *
 * usage:
 * <pre>
 * new Reflections(
 * new ConfigurationBuilder()
 * .filterInputsBy(new FilterBuilder().include("your project's common package prefix here..."))
 * .setUrls(ClasspathHelper.forClassLoader())
 * .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner().filterResultsBy(myClassAnnotationsFilter)));
</pre> *
 * <br></br>[.executorService] is used optionally used for parallel scanning. if value is null then scanning is done in a simple for loop
 *
 * defaults: accept all for [.inputsFilter],
 * [.executorService] is null,
 * [.serializer] is [org.reflections.serializers.XmlSerializer]
 */
class ConfigurationBuilder : Configuration {

    override var scanners: MutableSet<Scanner> = Sets.newHashSet<Scanner>(TypeAnnotationsScanner(), SubTypesScanner())
    override var urls: MutableSet<URL> = Sets.newHashSet<URL>()
    override var inputsFilter: ((String) -> Boolean)? = null
    override var executorService: ExecutorService? = null
    /**
     * get class loader, might be used for scanning or resolving methods/fields
     */
    /**
     * set class loader, might be used for resolving methods/fields
     */
    override var classLoaders: Array<out ClassLoader> = emptyArray()
    private var expandSuperTypes = true

    fun forPackages(vararg packages: String): ConfigurationBuilder {
        for (pkg in packages) {
            addUrls(ClasspathHelper.forPackage(pkg))
        }
        return this
    }

    /**
     * set the scanners instances for scanning different metadata
     */
    fun setScanners(vararg scanners: Scanner): ConfigurationBuilder {
        this.scanners.clear()
        return addScanners(*scanners)
    }

    /**
     * set the scanners instances for scanning different metadata
     */
    fun addScanners(vararg scanners: Scanner): ConfigurationBuilder {
        this.scanners.addAll(scanners.toSet())
        return this
    }

    /**
     * set the urls to be scanned
     *
     * use [org.reflections.util.ClasspathHelper] convenient methods to get the relevant urls
     */
    fun setUrls(urls: Iterable<URL>): ConfigurationBuilder {
        this.urls = Sets.newHashSet(urls)
        return this
    }

    fun filterInputsBy(inputsFilter: (String) -> Boolean): ConfigurationBuilder {
        this.inputsFilter = inputsFilter
        return this
    }

    /**
     * set the urls to be scanned
     *
     * use [org.reflections.util.ClasspathHelper] convenient methods to get the relevant urls
     */
    fun setUrls(vararg urls: URL): ConfigurationBuilder {
        this.urls = Sets.newHashSet(*urls)
        return this
    }

    /**
     * add urls to be scanned
     *
     * use [org.reflections.util.ClasspathHelper] convenient methods to get the relevant urls
     */
    fun addUrls(urls: Collection<URL>): ConfigurationBuilder {
        this.urls.addAll(urls)
        return this
    }

    /**
     * add urls to be scanned
     *
     * use [org.reflections.util.ClasspathHelper] convenient methods to get the relevant urls
     */
    fun addUrls(vararg urls: URL): ConfigurationBuilder {
        this.urls.addAll(Sets.newHashSet(*urls))
        return this
    }

    /**
     * returns the metadata adapter.
     * if javassist library exists in the classpath, this method returns [JavassistAdapter] otherwise defaults to [JavaReflectionAdapter].
     *
     * the [JavassistAdapter] is preferred in terms of performance and class loading.
     */
    override val metadataAdapter by lazy {
        try {
            JavassistAdapter() as MetadataAdapter<ClassWrapper, FieldWrapper, MethodWrapper>
        } catch (e: Throwable) {
            if (Reflections.log != null) {
                Reflections.log.warn("could not create JavassistAdapter, using JavaReflectionAdapter", e)
            }
            JavaReflectionAdapter() as MetadataAdapter<ClassWrapper, FieldWrapper, MethodWrapper>
        }
    }

    /**
     * sets the executor service used for scanning to ThreadPoolExecutor with core size as the given availableProcessors parameter.
     * the executor service spawns daemon threads by default.
     *
     * default is ThreadPoolExecutor with a single core
     */
    @JvmOverloads
    fun useParallelExecutor(availableProcessors: Int = Runtime.getRuntime().availableProcessors()): ConfigurationBuilder {
        val factory = ThreadFactoryBuilder().setDaemon(true).setNameFormat("org.reflections-scanner-%d").build()
        executorService = (Executors.newFixedThreadPool(availableProcessors, factory))
        return this
    }

    override var serializer: Serializer = XmlSerializer() //lazily defaults to XmlSerializer


    override fun shouldExpandSuperTypes(): Boolean {
        return expandSuperTypes
    }

    /**
     * if set to true, Reflections will expand super types after scanning.
     *
     * see [org.reflections.Reflections.expandSuperTypes]
     */
    fun setExpandSuperTypes(expandSuperTypes: Boolean): ConfigurationBuilder {
        this.expandSuperTypes = expandSuperTypes
        return this
    }

    /**
     * add class loader, might be used for resolving methods/fields
     */
    fun addClassLoader(classLoader: ClassLoader): ConfigurationBuilder {
        return addClassLoaders(classLoader)
    }

    /**
     * add class loader, might be used for resolving methods/fields
     */
    fun addClassLoaders(vararg classLoaders: ClassLoader): ConfigurationBuilder {
        this.classLoaders = if (this.classLoaders == null) classLoaders
        else ObjectArrays.concat(this.classLoaders!!, classLoaders, ClassLoader::class.java)
        return this
    }

    /**
     * add class loader, might be used for resolving methods/fields
     */
    fun addClassLoaders(classLoaders: Collection<ClassLoader>): ConfigurationBuilder {
        return addClassLoaders(*classLoaders.toTypedArray())
    }

    companion object {

        /**
         * constructs a [ConfigurationBuilder] using the given parameters, in a non statically typed way.
         * that is, each element in `params` is guessed by it's type and populated into the configuration.
         *
         *  * [String] - add urls using [ClasspathHelper.forPackage] ()}
         *  * [Class] - add urls using [ClasspathHelper.forClass]
         *  * [ClassLoader] - use these classloaders in order to find urls in ClasspathHelper.forPackage(), ClasspathHelper.forClass() and for resolving types
         *  * [Scanner] - use given scanner, overriding the default scanners
         *  * [URL] - add the given url for scanning
         *  * `Object[]` - flatten and use each element as above
         *
         *
         *
         * an input [FilterBuilder] will be set according to given packages.
         *
         * use any parameter type in any order. this constructor uses instanceof on each param and instantiate a [ConfigurationBuilder] appropriately.
         */
        fun build(vararg params: Any): ConfigurationBuilder {
            val builder = ConfigurationBuilder()

            //flatten
            val parameters = Lists.newArrayList<Any>()
            if (params != null) {
                for (param in params) {
                    if (param != null) {
                        if (param.javaClass.isArray) {
                            for (p in param as Array<Any>) {
                                if (p != null) {
                                    parameters.add(p)
                                }
                            }
                        } else if (param is Iterable<*>) {
                            for (p in param) {
                                if (p != null) {
                                    parameters.add(p)
                                }
                            }
                        } else {
                            parameters.add(param)
                        }
                    }
                }
            }

            val loaders = Lists.newArrayList<ClassLoader>()
            for (param in parameters) {
                if (param is ClassLoader) {
                    loaders.add(param)
                }
            }

            val classLoaders = if (loaders.isEmpty()) emptyArray() else loaders.toTypedArray()
            val filter = FilterBuilder()
            val scanners = Lists.newArrayList<Scanner>()

            for (param in parameters) {
                if (param is String) {
                    builder.addUrls(ClasspathHelper.forPackage(param, *classLoaders))
                    filter.includePackage(param)
                } else if (param is Class<*>) {
                    if (Scanner::class.java.isAssignableFrom(param)) {
                        try {
                            builder.addScanners(param.newInstance() as Scanner)
                        } catch (e: Exception) {
                            /*fallback*/
                        }

                    }
                    val url = ClasspathHelper.forClass(param, *classLoaders)
                    if (url != null) builder.addUrls(url)
                    filter.includePackage(param)
                } else if (param is Scanner) {
                    scanners.add(param)
                } else if (param is URL) {
                    builder.addUrls(param)
                } else if (param is ClassLoader) {
                    /* already taken care */
                } else if (param is Predicate<*>) {
                    filter.add(param as Predicate<String>)
                } else if (param is ExecutorService) {
                    builder.executorService = param
                } else if (Reflections.log != null) {
                    throw ReflectionsException("could not use param $param")
                }
            }

            if (builder.urls.isEmpty()) {
                if (classLoaders != null) {
                    builder.addUrls(ClasspathHelper.forClassLoader(*classLoaders)) //default urls getResources("")
                } else {
                    builder.addUrls(ClasspathHelper.forClassLoader()) //default urls getResources("")
                }
            }

            builder.inputsFilter = { filter.test(it) }
            if (!scanners.isEmpty()) {
                builder.setScanners(*scanners.toTypedArray())
            }
            if (!loaders.isEmpty()) {
                builder.addClassLoaders(loaders)
            }

            return builder
        }
    }
}
/**
 * sets the executor service used for scanning to ThreadPoolExecutor with core size as [java.lang.Runtime.availableProcessors]
 *
 * default is ThreadPoolExecutor with a single core
 */
