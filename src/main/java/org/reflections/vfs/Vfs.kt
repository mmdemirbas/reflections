package org.reflections.vfs

import org.reflections.Filter
import org.reflections.util.logError
import org.reflections.util.logWarn
import org.reflections.util.tryOrThrow
import org.reflections.util.whileNotNull
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.URISyntaxException
import java.net.URL
import java.net.URLDecoder
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry

/**
 * a simple virtual file system bridge
 *
 * use the [org.reflections.vfs.Vfs.fromURL] to get a [VfsDir],
 * then use [VfsDir.files] to iterate over the [VfsFile]
 *
 * for example:
 * ```
 * VfsDir dir = Vfs.fromURL(url);
 * Iterable<Vfs.VfsFile> files = dir.getFiles();
 * for (Vfs.VfsFile file : files) {
 *   InputStream is = file.openInputStream();
 * }
 * ```
 *
 * [org.reflections.vfs.Vfs.fromURL] uses static [BuiltinVfsUrlTypes] to resolve URLs.
 * It contains VfsTypes for handling for common resources such as local jar file, local directory, jar url, jar input stream and more.
 *
 * It can be plugged in with other [VfsUrlType] using [org.reflections.vfs.Vfs.addDefaultURLTypes] or [org.reflections.vfs.Vfs.defaultUrlTypes].
 *
 * for example:
 * ```
 * Vfs.addDefaultURLTypes(new Vfs.VfsUrlType() {
 *   public boolean matches(URL url)         {
 *     return url.getProtocol().equals("http");
 *   }
 *   public VfsDir createDir(final URL url) {
 *     return new HttpDir(url); //implement this type... (check out a naive implementation on VfsTest)
 *   }
 * });
 *
 * VfsDir dir = Vfs.fromURL(new URL("http://mirrors.ibiblio.org/pub/mirrors/maven2/org/slf4j/slf4j-api/1.5.6/slf4j-api-1.5.6.jar"));
 * ```
 *
 * use [org.reflections.vfs.Vfs.findFiles] to get an
 * iteration of files matching given name predicate over given list of urls
 */
object Vfs {
    /**
     * the default url types that will be used when issuing [org.reflections.vfs.Vfs.fromURL]
     */
    val defaultUrlTypes: MutableList<VfsUrlType> = BuiltinVfsUrlTypes.values().toMutableList()

    /**
     * tries to create a VfsDir from the given url, using the given urlTypes
     */
    fun fromURL(url: URL, urlTypes: List<VfsUrlType> = defaultUrlTypes) = urlTypes.mapNotNull { type ->
        try {
            when {
                type.matches(url) -> type.createDir(url)
                else              -> null
            }
        } catch (e: Throwable) {
            logWarn("could not create VfsDir using $type from url ${url.toExternalForm()}. skipping.", e)
            null
        }
    }.firstOrNull()
                                                                          ?: throw RuntimeException("could not create VfsDir from url, no matching VfsUrlType was found [${url.toExternalForm()}]\neither use fromURL(final URL url, final List<VfsUrlType> urlTypes) or use the static setDefaultURLTypes(final List<VfsUrlType> urlTypes) or addDefaultURLTypes(VfsUrlType urlType) with your specialized VfsUrlType.")

    /**
     * return an iterable of all [VfsFile] in given urls, starting with given packagePrefix and matching nameFilter
     */
    fun findFiles(inUrls: Collection<URL>, packagePrefix: String, nameFilter: Filter) =
            findFiles(inUrls) { vfsFile: VfsFile ->
                val path = vfsFile.relativePath ?: ""
                when {
                    path.startsWith(packagePrefix) -> {
                        val filename = path.substringAfter(packagePrefix)
                        !filename.isEmpty() && nameFilter.test(filename.substring(1))
                    }
                    else                           -> false
                }
            }

    /**
     * return an iterable of all [VfsFile] in given urls, matching filePredicate
     */
    fun findFiles(inUrls: Collection<URL>, filePredicate: (VfsFile) -> Boolean) = inUrls.flatMap { url ->
        try {
            fromURL(url).files.filter(filePredicate).toList()
        } catch (e: Throwable) {
            logError("could not findFiles for url. continuing. [$url]", e)
            emptyList<VfsFile>()
        }
    }

    /**
     * try to get [java.io.File] from url
     */
    fun getFile(url: URL): java.io.File? {
        try {
            val file = File(url.toURI().schemeSpecificPart)
            if (file.exists()) return file
        } catch (e: URISyntaxException) {
        }

        try {
            var path = URLDecoder.decode(url.path, "UTF-8")
            if (path.contains(".jar!")) path = path.substringBeforeLast(".jar!") + ".jar"
            val file = java.io.File(path)
            if (file.exists()) return file
        } catch (e: UnsupportedEncodingException) {
        }

        try {
            var path = url.toExternalForm().removePrefix("jar:").removePrefix("wsjar:").removePrefix("file:")
            if (path.contains(".jar!")) path = path.substringBefore(".jar!") + ".jar"
            if (path.contains(".war!")) path = path.substringBefore(".war!") + ".war"
            var file = java.io.File(path)
            if (file.exists()) return file

            path = path.replace("%20", " ")
            file = java.io.File(path)
            if (file.exists()) return file
        } catch (e: Exception) {
        }

        return null
    }
}

interface VfsDir : Closeable {
    // todo: use Path instead of String
    val path: String
    val files: Sequence<VfsFile>
}

interface VfsFile {
    // todo: use Path instead of String
    val name: String
    val relativePath: String?
    fun openInputStream(): InputStream
}

class JarInputDir(private val url: URL) : VfsDir {
    var jarInputStream: JarInputStream? = null
    var cursor: Long = 0
    var nextCursor: Long = 0
    override val path = url.path

    override val files: Sequence<VfsFile>
        get() {
            jarInputStream =
                    tryOrThrow("Could not open url connection") { JarInputStream(url.openConnection().getInputStream()) }
            return whileNotNull { jarInputStream?.nextJarEntry }.mapNotNull {
                val size = when {
                    it.size < 0 -> it.size + 0xffffffffL //JDK-6916399
                    else        -> it.size
                }
                nextCursor += size
                when {
                    it.isDirectory -> null
                    else           -> JarInputFile(it, this, cursor, nextCursor)
                }
            }
        }

    override fun close() {
        try {
            jarInputStream?.close()
        } catch (e: IOException) {
            logWarn("Could not close InputStream", e)
        }
    }
}

class JarInputFile(entry: ZipEntry, private val dir: JarInputDir, private val from: Long, private val to: Long) :
        VfsFile {
    override val name = entry.name.substringAfterLast('/')
    override val relativePath = entry.name

    override fun openInputStream() = object : InputStream() {
        override fun read() = when {
            dir.cursor in from..to -> {
                val read = dir.jarInputStream!!.read()
                dir.cursor++
                read
            }
            else                   -> -1
        }
    }
}

class SystemDir(private val file: java.io.File) : VfsDir {
    init {
        if (!file.isDirectory || !file.canRead()) throw RuntimeException("cannot use dir $file")
    }

    override val path = file.path?.replace("\\", "/") ?: "/NO-SUCH-DIRECTORY/"

    override val files
        get() = when {
            !file.exists() -> emptySequence()
            else           -> file.walkTopDown().filter { !it.isDirectory }.map {
                SystemFile(this, it)
            }
        }

    override fun close() = Unit
    override fun toString() = path
}

class SystemFile(root: SystemDir, private val file: java.io.File) : VfsFile {
    override val name = file.name
    override val relativePath = file.path.replace("\\", "/").removePrefix("${root.path}/")
    override fun openInputStream() = file.inputStream()
    override fun toString() = file.toString()
}

class ZipDir(val jarFile: JarFile) : VfsDir {
    override val path = jarFile.name

    override val files
        get() = jarFile.entries().asSequence().filter { !it.isDirectory }.map { ZipFile(this, it) }

    override fun close() = try {
        jarFile.close()
    } catch (e: IOException) {
        logWarn("Could not close JarFile", e)
    }

    override fun toString() = jarFile.name
}

class ZipFile(private val root: ZipDir, private val entry: ZipEntry) : VfsFile {
    override val name = entry.name.substringAfterLast('/')
    override val relativePath = entry.name
    override fun openInputStream() = root.jarFile.getInputStream(entry)
    override fun toString() = "${root.path}!${java.io.File.separatorChar}$entry"
}
