package org.reflections.util

import org.reflections.Reflections
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.net.URLDecoder
import java.util.*
import java.util.jar.Attributes.Name
import java.util.jar.JarFile
import javax.servlet.ServletContext

/**
 * Helper methods for working with the classpath.
 */
object ClasspathHelper {

    /**
     * Gets the current thread context class loader.
     * `Thread.currentThread().getContextClassLoader()`.
     *
     * @return the context class loader, may be null
     */
    fun contextClassLoader(): ClassLoader {
        return Thread.currentThread().contextClassLoader
    }

    /**
     * Gets the class loader of this library.
     * `Reflections.class.getClassLoader()`.
     *
     * @return the static library class loader, may be null
     */
    fun staticClassLoader(): ClassLoader {
        return Reflections::class.java.classLoader
    }

    /**
     * Returns an array of class Loaders initialized from the specified array.
     *
     *
     * If the input is null or empty, it defaults to both [.contextClassLoader] and [.staticClassLoader]
     *
     * @return the array of class loaders, not null
     */
    fun classLoaders(vararg classLoaders: ClassLoader?): Array<out ClassLoader> {
        if (classLoaders != null && classLoaders.size != 0) {
            return classLoaders.filterNotNull().toTypedArray()
        } else {
            val contextClassLoader = contextClassLoader()
            val staticClassLoader = staticClassLoader()
            return if (contextClassLoader != null) if (staticClassLoader != null && contextClassLoader !== staticClassLoader) arrayOf(
                    contextClassLoader,
                    staticClassLoader)
            else arrayOf(contextClassLoader)
            else arrayOf()

        }
    }

    /**
     * Returns a distinct collection of URLs based on a package name.
     *
     *
     * This searches for the package name as a resource, using [ClassLoader.getResources].
     * For example, `forPackage(org.reflections)` effectively returns URLs from the
     * classpath containing packages starting with `org.reflections`.
     *
     *
     * If the optional [ClassLoader]s are not specified, then both [.contextClassLoader]
     * and [.staticClassLoader] are used for [ClassLoader.getResources].
     *
     *
     * The returned URLs retainsthe order of the given `classLoaders`.
     *
     * @return the collection of URLs, not null
     */
    fun forPackage(name: String, vararg classLoaders: ClassLoader): Collection<URL> {
        return forResource(resourceName(name), *classLoaders)
    }

    /**
     * Returns a distinct collection of URLs based on a resource.
     *
     *
     * This searches for the resource name, using [ClassLoader.getResources].
     * For example, `forResource(test.properties)` effectively returns URLs from the
     * classpath containing files of that name.
     *
     *
     * If the optional [ClassLoader]s are not specified, then both [.contextClassLoader]
     * and [.staticClassLoader] are used for [ClassLoader.getResources].
     *
     *
     * The returned URLs retains the order of the given `classLoaders`.
     *
     * @return the collection of URLs, not null
     */
    fun forResource(resourceName: String?, vararg classLoaders: ClassLoader): Collection<URL> {
        val result = ArrayList<URL>()
        val loaders = classLoaders(*classLoaders)
        for (classLoader in loaders) {
            try {
                val urls = classLoader.getResources(resourceName)
                while (urls.hasMoreElements()) {
                    val url = urls.nextElement()
                    val index = url.toExternalForm().lastIndexOf(resourceName!!)
                    if (index == -1) {
                        result.add(url)
                    } else {
                        // Add old url as contextUrl to support exotic url handlers
                        result.add(URL(url, url.toExternalForm().substring(0, index)))
                    }
                }
            } catch (e: IOException) {
                if (Reflections.log != null) {
                    Reflections.log.error("error getting resources for " + resourceName!!, e)
                }
            }

        }
        return distinctUrls(result)
    }

    /**
     * Returns the URL that contains a `Class`.
     *
     *
     * This searches for the class using [ClassLoader.getResource].
     *
     *
     * If the optional [ClassLoader]s are not specified, then both [.contextClassLoader]
     * and [.staticClassLoader] are used for [ClassLoader.getResources].
     *
     * @return the URL containing the class, null if not found
     */
    fun forClass(aClass: Class<*>, vararg classLoaders: ClassLoader): URL? {
        val loaders = classLoaders(*classLoaders)
        val resourceName = aClass.name.replace(".", "/") + ".class"
        for (classLoader in loaders) {
            try {
                val url = classLoader.getResource(resourceName)
                if (url != null) {
                    val normalizedUrl =
                            url.toExternalForm()
                                .substring(0,
                                           url.toExternalForm().lastIndexOf(aClass.getPackage().name.replace(".", "/")))
                    return URL(normalizedUrl)
                }
            } catch (e: MalformedURLException) {
                if (Reflections.log != null) {
                    Reflections.log.warn("Could not get URL", e)
                }
            }

        }
        return null
    }

    /**
     * Returns a distinct collection of URLs based on URLs derived from class loaders.
     *
     *
     * This finds the URLs using [URLClassLoader.getURLs] using the specified
     * class loader, searching up the parent hierarchy.
     *
     *
     * If the optional [ClassLoader]s are not specified, then both [.contextClassLoader]
     * and [.staticClassLoader] are used for [ClassLoader.getResources].
     *
     *
     * The returned URLs retains the order of the given `classLoaders`.
     *
     * @return the collection of URLs, not null
     */
    @JvmOverloads
    fun forClassLoader(vararg classLoaders: ClassLoader? = classLoaders()): Collection<URL> {
        val result = ArrayList<URL>()
        val loaders = classLoaders(*classLoaders)
        for (loader in loaders) {
            var classLoader: ClassLoader? = loader
            while (classLoader != null) {
                if (classLoader is URLClassLoader) {
                    val urls = classLoader.urLs
                    if (urls != null) {
                        result.addAll(Arrays.asList(*urls))
                    }
                }
                classLoader = classLoader.parent
            }
        }
        return distinctUrls(result)
    }

    /**
     * Returns a distinct collection of URLs based on the `java.class.path` system property.
     *
     *
     * This finds the URLs using the `java.class.path` system property.
     *
     *
     * The returned collection of URLs retains the classpath order.
     *
     * @return the collection of URLs, not null
     */
    fun forJavaClassPath(): Collection<URL> {
        val urls = ArrayList<URL>()
        val javaClassPath = System.getProperty("java.class.path")
        if (javaClassPath != null) {
            for (path in javaClassPath.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                try {
                    urls.add(File(path).toURI().toURL())
                } catch (e: Exception) {
                    if (Reflections.log != null) {
                        Reflections.log.warn("Could not get URL", e)
                    }
                }

            }
        }
        return distinctUrls(urls)
    }

    /**
     * Returns a distinct collection of URLs based on the `WEB-INF/lib` folder.
     *
     *
     * This finds the URLs using the [ServletContext].
     *
     *
     * The returned URLs retains the order of the given `classLoaders`.
     *
     * @return the collection of URLs, not null
     */
    fun forWebInfLib(servletContext: ServletContext): Collection<URL> {
        val urls = ArrayList<URL>()
        val resourcePaths = servletContext.getResourcePaths("/WEB-INF/lib") ?: return urls
        for (urlString in resourcePaths) {
            try {
                urls.add(servletContext.getResource(urlString as String))
            } catch (e: MalformedURLException) { /*fuck off*/
            }

        }
        return distinctUrls(urls)
    }

    /**
     * Returns the URL of the `WEB-INF/classes` folder.
     *
     *
     * This finds the URLs using the [ServletContext].
     *
     * @return the collection of URLs, not null
     */
    fun forWebInfClasses(servletContext: ServletContext): URL? {
        try {
            val path = servletContext.getRealPath("/WEB-INF/classes")
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    return file.toURL()
                }
            } else {
                return servletContext.getResource("/WEB-INF/classes")
            }
        } catch (e: MalformedURLException) { /*fuck off*/
        }

        return null
    }

    /**
     * Returns a distinct collection of URLs from a single URL based on the Manifest information.
     *
     *
     * The `MANIFEST.MF` file can contain a `Class-Path` entry that defines additional
     * jar files to be included on the classpath. This method takes a single URL, tries to
     * resolve it as a jar file, and if so, adds any additional manifest classpaths.
     * The returned collection of URLs will always contain the input URL.
     *
     * @return the collection of URLs, not null
     */
    fun forManifest(url: URL): Collection<URL> {
        val result = ArrayList<URL>()
        result.add(url)
        try {
            val part = cleanPath(url)
            val jarFile = File(part)
            val myJar = JarFile(part)
            var validUrl = tryToGetValidUrl(jarFile.path, File(part).parent, part)
            if (validUrl != null) {
                result.add(validUrl)
            }
            val manifest = myJar.manifest
            if (manifest != null) {
                val classPath = manifest.mainAttributes.getValue(Name("Class-Path"))
                if (classPath != null) {
                    for (jar in classPath.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                        validUrl = tryToGetValidUrl(jarFile.path, File(part).parent, jar)
                        if (validUrl != null) {
                            result.add(validUrl)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // don't do anything, we're going on the assumption it is a jar, which could be wrong
        }

        return distinctUrls(result)
    }

    /**
     * Returns a distinct collection of URLs by expanding the specified URLs with Manifest information.
     *
     *
     * The `MANIFEST.MF` file can contain a `Class-Path` entry that defines additional
     * jar files to be included on the classpath. This method takes each URL in turn, tries to
     * resolve it as a jar file, and if so, adds any additional manifest classpaths.
     * The returned collection of URLs will always contain all the input URLs.
     *
     *
     * The returned URLs retains the input order.
     *
     * @return the collection of URLs, not null
     */
    @JvmOverloads
    fun forManifest(urls: Iterable<URL> = forClassLoader()): Collection<URL> {
        val result = ArrayList<URL>()
        // determine if any of the URLs are JARs, and get any dependencies
        for (url in urls) {
            result.addAll(forManifest(url))
        }
        return distinctUrls(result)
    }

    //a little bit cryptic...
    internal fun tryToGetValidUrl(workingDir: String, path: String, filename: String): URL? {
        try {
            if (File(filename).exists()) {
                return File(filename).toURI().toURL()
            }
            if (File(path + File.separator + filename).exists()) {
                return File(path + File.separator + filename).toURI().toURL()
            }
            if (File(workingDir + File.separator + filename).exists()) {
                return File(workingDir + File.separator + filename).toURI().toURL()
            }
            if (File(URL(filename).file).exists()) {
                return File(URL(filename).file).toURI().toURL()
            }
        } catch (e: MalformedURLException) {
            // don't do anything, we're going on the assumption it is a jar, which could be wrong
        }

        return null
    }

    /**
     * Cleans the URL.
     *
     * @param url the URL to clean, not null
     *
     * @return the path, not null
     */
    fun cleanPath(url: URL): String {
        var path = url.path
        try {
            path = URLDecoder.decode(path, "UTF-8")
        } catch (e: UnsupportedEncodingException) { /**/
        }

        if (path.startsWith("jar:")) {
            path = path.substring("jar:".length)
        }
        if (path.startsWith("file:")) {
            path = path.substring("file:".length)
        }
        if (path.endsWith("!/")) {
            path = path.substring(0, path.lastIndexOf("!/")) + '/'
        }
        return path
    }

    private fun resourceName(name: String?): String? {
        if (name != null) {
            var resourceName = name.replace(".", "/")
            resourceName = resourceName.replace("\\", "/")
            if (resourceName.startsWith("/")) {
                resourceName = resourceName.substring(1)
            }
            return resourceName
        }
        return null
    }

    //http://michaelscharf.blogspot.co.il/2006/11/javaneturlequals-and-hashcode-make.html
    private fun distinctUrls(urls: Collection<URL>): Collection<URL> {
        val distinct = LinkedHashMap<String, URL>(urls.size)
        for (url in urls) {
            distinct[url.toExternalForm()] = url
        }
        return distinct.values
    }
}
/**
 * Returns a distinct collection of URLs based on URLs derived from class loaders.
 *
 *
 * This finds the URLs using [URLClassLoader.getURLs] using both
 * [.contextClassLoader] and [.staticClassLoader].
 *
 *
 * The returned URLs retains the order of the given `classLoaders`.
 *
 * @return the collection of URLs, not null
 */
/**
 * Returns a distinct collection of URLs based on URLs derived from class loaders expanded with Manifest information.
 *
 *
 * The `MANIFEST.MF` file can contain a `Class-Path` entry that defines
 * additional jar files to be included on the classpath. This method finds the jar files
 * using the [.contextClassLoader] and [.staticClassLoader], before
 * searching for any additional manifest classpaths.
 *
 * @return the collection of URLs, not null
 */

