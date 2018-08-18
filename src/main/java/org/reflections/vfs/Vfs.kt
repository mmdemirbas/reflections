package org.reflections.vfs

import org.reflections.Reflections
import org.reflections.ReflectionsException
import org.reflections.util.ClasspathHelper
import org.reflections.util.Utils
import org.reflections.vfs.Vfs.DefaultUrlTypes
import org.reflections.vfs.Vfs.Dir
import org.reflections.vfs.Vfs.File
import org.reflections.vfs.Vfs.UrlType
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.JarURLConnection
import java.net.URISyntaxException
import java.net.URL
import java.net.URLDecoder
import java.util.*
import java.util.jar.JarFile

/**
 * a simple virtual file system bridge
 *
 * use the [org.reflections.vfs.Vfs.fromURL] to get a [Dir],
 * then use [Dir.getFiles] to iterate over the [File]
 *
 * for example:
 * <pre>
 * Vfs.Dir dir = Vfs.fromURL(url);
 * Iterable<Vfs.File> files = dir.getFiles();
 * for (Vfs.File file : files) {
 * InputStream is = file.openInputStream();
 * }
</Vfs.File></pre> *
 *
 * [org.reflections.vfs.Vfs.fromURL] uses static [DefaultUrlTypes] to resolve URLs.
 * It contains VfsTypes for handling for common resources such as local jar file, local directory, jar url, jar input stream and more.
 *
 * It can be plugged in with other [UrlType] using [org.reflections.vfs.Vfs.addDefaultURLTypes] or [org.reflections.vfs.Vfs.setDefaultURLTypes].
 *
 * for example:
 * <pre>
 * Vfs.addDefaultURLTypes(new Vfs.UrlType() {
 * public boolean matches(URL url)         {
 * return url.getProtocol().equals("http");
 * }
 * public Vfs.Dir createDir(final URL url) {
 * return new HttpDir(url); //implement this type... (check out a naive implementation on VfsTest)
 * }
 * });
 *
 * Vfs.Dir dir = Vfs.fromURL(new URL("http://mirrors.ibiblio.org/pub/mirrors/maven2/org/slf4j/slf4j-api/1.5.6/slf4j-api-1.5.6.jar"));
</pre> *
 *
 * use [org.reflections.vfs.Vfs.findFiles] to get an
 * iteration of files matching given name predicate over given list of urls
 */
object Vfs {

    private var defaultUrlTypes: MutableList<UrlType> = DefaultUrlTypes.values().toMutableList()

    /**
     * an abstract vfs dir
     */
    interface Dir {

        val path: String

        val files: Sequence<File>

        fun close()
    }

    /**
     * an abstract vfs file
     */
    interface File {

        val name: String

        val relativePath: String?

        @Throws(IOException::class)
        fun openInputStream(): InputStream
    }

    /**
     * a matcher and factory for a url
     */
    interface UrlType {

        fun matches(url: URL): Boolean

        @Throws(Exception::class)
        fun createDir(url: URL): Dir?
    }

    /**
     * the default url types that will be used when issuing [org.reflections.vfs.Vfs.fromURL]
     */
    fun getDefaultUrlTypes(): List<UrlType> {
        return defaultUrlTypes
    }

    /**
     * sets the static default url types. can be used to statically plug in urlTypes
     */
    fun setDefaultURLTypes(urlTypes: MutableList<UrlType>) {
        defaultUrlTypes = urlTypes
    }

    /**
     * add a static default url types to the beginning of the default url types list. can be used to statically plug in urlTypes
     */
    fun addDefaultURLTypes(urlType: UrlType) {
        defaultUrlTypes.add(0, urlType)
    }

    /**
     * tries to create a Dir from the given url, using the given urlTypes
     */
    @JvmOverloads
    @JvmStatic
    fun fromURL(url: URL, urlTypes: List<UrlType> = defaultUrlTypes): Dir {
        for (type in urlTypes) {
            try {
                if (type.matches(url)) {
                    val dir = type.createDir(url)
                    if (dir != null) {
                        return dir
                    }
                }
            } catch (e: Throwable) {
                if (Reflections.log != null) {
                    Reflections.log.warn("could not create Dir using " + type + " from url " + url.toExternalForm() + ". skipping.",
                                         e)
                }
            }

        }

        throw ReflectionsException("could not create Vfs.Dir from url, no matching UrlType was found [" + url.toExternalForm() + "]\n" + "either use fromURL(final URL url, final List<UrlType> urlTypes) or " + "use the static setDefaultURLTypes(final List<UrlType> urlTypes) or addDefaultURLTypes(UrlType urlType) " + "with your specialized UrlType.")
    }

    /**
     * tries to create a Dir from the given url, using the given urlTypes
     */
    fun fromURL(url: URL, vararg urlTypes: UrlType): Dir {
        return fromURL(url, listOf(*urlTypes))
    }

    /**
     * return an iterable of all [File] in given urls, starting with given packagePrefix and matching nameFilter
     */
    fun findFiles(inUrls: Collection<URL>, packagePrefix: String, nameFilter: (String) -> Boolean): Iterable<File> {
        val fileNamePredicate = { file: File ->
            val path = file.relativePath ?: ""
            if (path.startsWith(packagePrefix)) {
                val filename = path.substring(path.indexOf(packagePrefix) + packagePrefix.length)
                !Utils.isEmpty(filename) && nameFilter(filename.substring(1))
            } else {
                false
            }
        }

        return findFiles(inUrls, fileNamePredicate)
    }

    /**
     * return an iterable of all [File] in given urls, matching filePredicate
     */
    fun findFiles(inUrls: Collection<URL>, filePredicate: (File) -> Boolean): Iterable<File> {
        var result: Iterable<File> = ArrayList()

        for (url in inUrls) {
            try {
                result += fromURL(url).files.filter(filePredicate)
            } catch (e: Throwable) {
                if (Reflections.log != null) {
                    Reflections.log.error("could not findFiles for url. continuing. [" + url + ']'.toString(), e)
                }
            }

        }

        return result
    }

    /**
     * try to get [java.io.File] from url
     */
    fun getFile(url: URL): java.io.File? {
        var file: java.io.File
        var path: String

        try {
            path = url.toURI().schemeSpecificPart
            file = java.io.File(path)
            if (file.exists()) {
                return file
            }
        } catch (e: URISyntaxException) {
        }

        try {
            path = URLDecoder.decode(url.path, "UTF-8")
            if (path.contains(".jar!")) {
                path = path.substring(0, path.lastIndexOf(".jar!") + ".jar".length)
            }
            file = java.io.File(path)
            if (file.exists()) {
                return file
            }

        } catch (e: UnsupportedEncodingException) {
        }

        try {
            path = url.toExternalForm()
            if (path.startsWith("jar:")) {
                path = path.substring("jar:".length)
            }
            if (path.startsWith("wsjar:")) {
                path = path.substring("wsjar:".length)
            }
            if (path.startsWith("file:")) {
                path = path.substring("file:".length)
            }
            if (path.contains(".jar!")) {
                path = path.substring(0, path.indexOf(".jar!") + ".jar".length)
            }
            if (path.contains(".war!")) {
                path = path.substring(0, path.indexOf(".war!") + ".war".length)
            }
            file = java.io.File(path)
            if (file.exists()) {
                return file
            }

            path = path.replace("%20", " ")
            file = java.io.File(path)
            if (file.exists()) {
                return file
            }

        } catch (e: Exception) {
        }

        return null
    }

    private fun hasJarFileInPath(url: URL): Boolean {
        return url.toExternalForm().matches(".*\\.jar(\\!.*|$)".toRegex())
    }

    /**
     * default url types used by [org.reflections.vfs.Vfs.fromURL]
     *
     *
     *
     * jarFile - creates a [org.reflections.vfs.ZipDir] over jar file
     *
     * jarUrl - creates a [org.reflections.vfs.ZipDir] over a jar url (contains ".jar!/" in it's name), using Java's [JarURLConnection]
     *
     * directory - creates a [org.reflections.vfs.SystemDir] over a file system directory
     *
     * jboss vfs - for protocols vfs, using jboss vfs (should be provided in classpath)
     *
     * jboss vfsfile - creates a [UrlTypeVFS] for protocols vfszip and vfsfile.
     *
     * bundle - for bundle protocol, using eclipse FileLocator (should be provided in classpath)
     *
     * jarInputStream - creates a [JarInputDir] over jar files, using Java's JarInputStream
     */
    enum class DefaultUrlTypes : UrlType {

        jarFile {
            override fun matches(url: URL): Boolean {
                return url.protocol == "file" && hasJarFileInPath(url)
            }

            @Throws(Exception::class)
            override fun createDir(url: URL): Dir? {
                return ZipDir(JarFile(getFile(url)!!))
            }
        },

        jarUrl {
            override fun matches(url: URL): Boolean {
                return ("jar" == url.protocol || "zip" == url.protocol || "wsjar" == url.protocol)
            }

            @Throws(Exception::class)
            override fun createDir(url: URL): Dir? {
                try {
                    val urlConnection = url.openConnection()
                    if (urlConnection is JarURLConnection) {
                        urlConnection.setUseCaches(false)
                        return ZipDir(urlConnection.jarFile)
                    }
                } catch (e: Throwable) { /*fallback*/
                }

                val file = getFile(url)
                return if (file != null) {
                    ZipDir(JarFile(file))
                } else null
            }
        },

        directory {
            override fun matches(url: URL): Boolean {
                if (url.protocol == "file" && !hasJarFileInPath(url)) {
                    val file = getFile(url)
                    return file != null && file.isDirectory
                } else {
                    return false
                }
            }

            override fun createDir(url: URL): Dir? {
                return SystemDir(getFile(url))
            }
        },

        jboss_vfs {
            override fun matches(url: URL): Boolean {
                return url.protocol == "vfs"
            }

            @Throws(Exception::class)
            override fun createDir(url: URL): Dir? {
                val content = url.openConnection().content
                val virtualFile = ClasspathHelper.contextClassLoader()!!.loadClass("org.jboss.vfs.VirtualFile")
                val physicalFile = virtualFile.getMethod("getPhysicalFile").invoke(content) as java.io.File
                val name = virtualFile.getMethod("getName").invoke(content) as String
                var file = java.io.File(physicalFile.parentFile, name)
                if (!file.exists() || !file.canRead()) {
                    file = physicalFile
                }
                return if (file.isDirectory) SystemDir(file) else ZipDir(JarFile(file))
            }
        },

        jboss_vfsfile {
            override fun matches(url: URL): Boolean {
                return "vfszip" == url.protocol || "vfsfile" == url.protocol
            }

            override fun createDir(url: URL): Dir? {
                return UrlTypeVFS().createDir(url)
            }
        },

        bundle {
            override fun matches(url: URL): Boolean {
                return url.protocol.startsWith("bundle")
            }

            @Throws(Exception::class)
            override fun createDir(url: URL): Dir? {
                return fromURL(ClasspathHelper.contextClassLoader().loadClass("org.eclipse.core.runtime.FileLocator").getMethod(
                        "resolve",
                        URL::class.java).invoke(null, url) as URL)
            }
        },

        jarInputStream {
            override fun matches(url: URL): Boolean {
                return url.toExternalForm().contains(".jar")
            }

            override fun createDir(url: URL): Dir? {
                return JarInputDir(url)
            }
        }
    }
}
/**
 * tries to create a Dir from the given url, using the defaultUrlTypes
 */
