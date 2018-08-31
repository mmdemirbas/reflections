package org.reflections.vfs

import org.reflections.Filter
import org.reflections.util.canRead
import org.reflections.util.contextClassLoader
import org.reflections.util.exists
import org.reflections.util.inputStream
import org.reflections.util.isDirectory
import org.reflections.util.isFile
import org.reflections.util.logError
import org.reflections.util.logWarn
import org.reflections.util.name
import org.reflections.util.nullIfNotExists
import org.reflections.util.path
import org.reflections.util.tryOrDefault
import org.reflections.util.tryOrIgnore
import org.reflections.util.tryOrNull
import org.reflections.util.tryOrThrow
import org.reflections.util.walkTopDown
import org.reflections.util.whileNotNull
import java.io.Closeable
import java.io.InputStream
import java.net.JarURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.regex.Pattern
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
 * Iterable<Vfs.VfsFile> files = dir.files();
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
    fun fromURL(url: URL,
                fileSystem: FileSystem = FileSystems.getDefault(),
                urlTypes: List<VfsUrlType> = defaultUrlTypes) = urlTypes.mapNotNull { type ->
        try {
            type.createDir(url, fileSystem)
        } catch (e: Throwable) {
            logWarn("could not create VfsDir using $type from url ${url.toExternalForm()}. skipping.", e)
            null
        }
    }.firstOrNull()
                                                                ?: throw RuntimeException("could not create VfsDir from url, no matching VfsUrlType was found [${url.toExternalForm()}]\neither use fromURL(final URL url, final List<VfsUrlType> urlTypes) or use the static setDefaultURLTypes(final List<VfsUrlType> urlTypes) or addDefaultURLTypes(VfsUrlType urlType) with your specialized VfsUrlType.")

    /**
     * return an iterable of all [VfsFile] in given urls, starting with given packagePrefix and matching nameFilter
     */
    fun findFiles(inUrls: Collection<URL>,
                  packagePrefix: String,
                  nameFilter: Filter,
                  fileSystem: FileSystem = FileSystems.getDefault()) =
            findFiles(inUrls = inUrls, fileSystem = fileSystem) { vfsFile: VfsFile ->
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
    fun findFiles(inUrls: Collection<URL>,
                  fileSystem: FileSystem = FileSystems.getDefault(),
                  filePredicate: (VfsFile) -> Boolean) = inUrls.flatMap { url ->
        try {
            fromURL(url = url, fileSystem = fileSystem).files().filter(filePredicate).toList()
        } catch (e: Throwable) {
            logError("could not findFiles for url. continuing. [$url]", e)
            emptyList<VfsFile>()
        }
    }

    /**
     * try to get [Path] from url
     */
    fun getFile(url: URL, fileSystem: FileSystem = FileSystems.getDefault()) = tryOrNull {
        fileSystem.getPath(url.toURI().schemeSpecificPart).nullIfNotExists()
    } ?: tryOrNull {
        val path = URLDecoder.decode(url.path, "UTF-8")
        fileSystem.getPath(if (path.contains(".jar!")) path.substringBeforeLast(".jar!") + ".jar" else path)
            .nullIfNotExists()
    } ?: tryOrNull {
        var path = url.toExternalForm().removePrefix("jar:").removePrefix("wsjar:").removePrefix("file:")
        if (path.contains(".jar!")) path = path.substringBefore(".jar!") + ".jar"
        if (path.contains(".war!")) path = path.substringBefore(".war!") + ".war"
        fileSystem.getPath(path).nullIfNotExists() ?: fileSystem.getPath(path.replace("%20", " ")).nullIfNotExists()
    }
}


abstract class VfsDir(val path: Path) : Closeable {
    abstract fun files(): Sequence<VfsFile>
    override fun toString() = path.toString()
}

// todo: convert to data class or something
interface VfsFile {
    val name: String

    // todo: use Path instead of String
    val relativePath: String?

    fun openInputStream(): InputStream
}

class SystemDir(path: Path) : VfsDir(path) {
    init {
        if (!path.isDirectory || !path.canRead()) throw RuntimeException("cannot use dir $path")
    }

    override fun files() = when {
        !path.exists() -> emptySequence()
        else           -> path.walkTopDown().filter { !it.isDirectory }.map { SystemFile(this, it) }
    }

    override fun close() = Unit
}

class ZipDir(val jarFile: JarFile, val fileSystem: FileSystem) : VfsDir(fileSystem.getPath(jarFile.name)) {
    // todo: JarFile'ı file'a bulaşmadan path'ten create edebilmeliyiz
    constructor(path: Path) : this(JarFile(path.toFile()), path.fileSystem)

    override fun files() =
            jarFile.entries().asSequence().filter { !it.isDirectory }.map { ZipFile(this, it, fileSystem) }

    override fun close() = tryOrIgnore { jarFile.close() }
}

class JarInputDir(private val url: URL, val fileSystem: FileSystem) : VfsDir(fileSystem.getPath(url.path)) {
    var jarInputStream: JarInputStream? = null
    var cursor: Long = 0
    var nextCursor: Long = 0

    // todo: bunu sequence olmaktan çıkar yada sequence bitince jar input stream otomatik kapatılsın bunun bir yolunu bul
    override fun files(): Sequence<VfsFile> {
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
                else           -> JarInputFile(it, this, cursor, nextCursor, fileSystem)
            }
        }
    }

    override fun close() = tryOrIgnore { jarInputStream?.close() }
}

class SystemFile(root: SystemDir, private val file: Path) : VfsFile {
    override val name = file.name
    override val relativePath = file.path.replace("\\", "/").removePrefix("${root.path}/")
    override fun openInputStream() = file.inputStream()
    override fun toString() = file.toString()
}

class ZipFile(private val root: ZipDir, private val entry: ZipEntry, val fileSystem: FileSystem) : VfsFile {
    override val name = entry.name.substringAfterLast(fileSystem.separator)
    override val relativePath = entry.name
    override fun openInputStream() = root.jarFile.getInputStream(entry)
    override fun toString() = "${root.path}!${fileSystem.separator}$entry"
}

class JarInputFile(entry: ZipEntry,
                   private val dir: JarInputDir,
                   private val from: Long,
                   private val to: Long,
                   fileSystem: FileSystem) : VfsFile {
    override val name = entry.name.substringAfterLast(fileSystem.separator)
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


interface VfsUrlType {
    fun createDir(url: URL, fileSystem: FileSystem = FileSystems.getDefault()): VfsDir?
}

/**
 * built-in url types used by [org.reflections.vfs.Vfs.fromURL]
 *
 *
 * JAR_FILE - creates a [org.reflections.vfs.ZipDir] over jar file
 *
 * JAR_URL - creates a [org.reflections.vfs.ZipDir] over a jar url (contains ".jar!/" in it's name), using Java's [JarURLConnection]
 *
 * DIRECTORY - creates a [org.reflections.vfs.SystemDir] over a file system directory
 *
 * jboss vfs - for protocols vfs, using jboss vfs (should be provided in classpath)
 *
 * jboss vfsfile - creates a [UrlTypeVFS] for protocols vfszip and vfsfile.
 *
 * BUNDLE - for bundle protocol, using eclipse FileLocator (should be provided in classpath)
 *
 * JAR_STREAM - creates a [JarInputDir] over jar files, using Java's JarInputStream
 */
enum class BuiltinVfsUrlTypes : VfsUrlType {
    JAR_FILE {
        override fun createDir(url: URL, fileSystem: FileSystem) = when {
            url.protocol == "file" && url.hasJarFileInPath() -> ZipDir(Vfs.getFile(url, fileSystem)!!)
            else                                             -> null
        }
    },

    JAR_STREAM {
        override fun createDir(url: URL, fileSystem: FileSystem) = when {
            url.toExternalForm().contains(".jar") -> JarInputDir(url, fileSystem)
            else                                  -> null
        }
    },

    JAR_URL {
        override fun createDir(url: URL, fileSystem: FileSystem) = when {
            url.protocol in listOf("jar", "zip", "wsjar") -> {
                tryOrNull {
                    val urlConnection = url.openConnection()
                    if (urlConnection is JarURLConnection) {
                        urlConnection.setUseCaches(false)
                        ZipDir(urlConnection.jarFile, fileSystem)
                    } else null
                } ?: Vfs.getFile(url, fileSystem)?.let { ZipDir(it) }
            }
            else                                          -> null
        }
    },

    DIRECTORY {
        override fun createDir(url: URL, fileSystem: FileSystem): SystemDir? {
            if (url.protocol != "file") return null
            if (url.hasJarFileInPath()) return null
            val file = Vfs.getFile(url, fileSystem)
            if (file?.isDirectory == true) return SystemDir(file)
            return null
        }
    },

    JBOSS_VFS {
        override fun createDir(url: URL, fileSystem: FileSystem) = when {
            url.protocol == "vfs" -> {
                val content = url.openConnection().content
                val virtualFile = contextClassLoader()!!.loadClass("org.jboss.vfs.VirtualFile")
                // todo: physicalFile'ın direk Path olarak alınması mümkün olabilir mi?
                // todo: Jboss'u test dependency olarak ekleyip bunu test edebiliriz
                val physicalFile = (virtualFile.getMethod("getPhysicalFile").invoke(content) as java.io.File).toPath()
                val name = virtualFile.getMethod("getName").invoke(content) as String
                var file = physicalFile.parent.resolve(name)
                if (!file.exists() || !file.canRead()) file = physicalFile
                if (file.isDirectory) SystemDir(file) else ZipDir(file)
            }
            else                  -> null
        }
    },

    JBOSS_VFSFILE {
        private val VFSZIP = "vfszip"
        private val VFSFILE = "vfsfile"

        override fun createDir(url: URL, fileSystem: FileSystem) = when {
            url.protocol in listOf(VFSZIP, VFSFILE) -> tryOrNull {
                ZipDir(JarFile(adaptURL(url, fileSystem).file),
                       fileSystem)
            }
            else                                    -> null
        }

        private fun adaptURL(url: URL, fileSystem: FileSystem) = tryOrDefault(url) {
            when (url.protocol) {
                VFSFILE -> URL(url.toString().replace(VFSFILE, "file"))
                VFSZIP  -> {
                    var ret: URL? = null
                    val path = url.path
                    var pos = 0
                    while (pos != -1) {
                        val matcher = Pattern.compile("\\.[ejprw]ar/").matcher(path)
                        pos = if (matcher.find(pos)) matcher.end() else -1
                        if (pos > 0) {
                            val file = fileSystem.getPath(path.substring(0, pos - 1))
                            if (file.exists() && file.isFile) {
                                val zipFile = path.substring(0, pos - 1)
                                var zipPath = path.substring(pos)
                                var numSubs = 1
                                listOf(".ear/", ".jar/", ".war/", ".sar/", ".har/", ".par/").forEach { ext ->
                                    while (zipPath.contains(ext)) {
                                        numSubs++
                                        zipPath = zipPath.replace(ext, ext.substring(0, 4) + '!')
                                    }
                                }
                                val prefix = "zip:".repeat(numSubs)
                                ret = when {
                                    zipPath.trim { it <= ' ' }.isEmpty() -> URL("$prefix/$zipFile")
                                    else                                 -> URL("$prefix/$zipFile!$zipPath")
                                }
                                break
                            }
                        }
                    }
                    ret ?: throw RuntimeException("Unable to identify the real zip file in path '$path'.")
                }
                else    -> url
            }
        }
    },

    BUNDLE {
        override fun createDir(url: URL, fileSystem: FileSystem) = when {
            url.protocol.startsWith("bundle") -> Vfs.fromURL(url = contextClassLoader()!!.loadClass("org.eclipse.core.runtime.FileLocator").getMethod(
                    "resolve",
                    URL::class.java).invoke(null, url) as URL, fileSystem = fileSystem)
            else                              -> null
        }
    },
}

private fun URL.hasJarFileInPath() = toExternalForm().matches(".*\\.jar(!.*|$)".toRegex())
