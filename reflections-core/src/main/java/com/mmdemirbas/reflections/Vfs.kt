package com.mmdemirbas.reflections

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
 * use the [org.reflections.vfs.Vfs.fromURL] to get a [VirtualDir],
 * then use [VirtualDir.files] to iterate over the [VirtualFile]
 *
 * for example:
 * ```
 * VirtualDir dir = Vfs.fromURL(url);
 * Iterable<Vfs.VirtualFile> files = dir.files();
 * for (Vfs.VirtualFile file : files) {
 *   InputStream is = file.openInputStream();
 * }
 * ```
 *
 * [org.reflections.vfs.Vfs.fromURL] uses static [VfsUrlTypes] to resolve URLs.
 * It contains VfsTypes for handling for common resources such as local jar file, local directory, jar url, jar input stream and more.
 *
 * It can be plugged in with other [VfsUrlTypeParser] using [org.reflections.vfs.Vfs.addDefaultURLTypes] or [org.reflections.vfs.Vfs.defaultUrlTypes].
 *
 * for example:
 * ```
 * Vfs.addDefaultURLTypes(new Vfs.VfsUrlTypeParser() {
 *   public boolean matches(URL url)         {
 *     return url.getProtocol().equals("http");
 *   }
 *   public VirtualDir createDir(final URL url) {
 *     return new HttpDir(url); //implement this type... (check out a naive implementation on VfsTest)
 *   }
 * });
 *
 * VirtualDir dir = Vfs.fromURL(new URL("http://mirrors.ibiblio.org/pub/mirrors/maven2/org/slf4j/slf4j-api/1.5.6/slf4j-api-1.5.6.jar"));
 * ```
 *
 * use [org.reflections.vfs.Vfs.findFiles] to get an
 * iteration of files matching given name predicate over given list of urls
 */
object Vfs {
    /**
     * the default url types that will be used when issuing [org.reflections.vfs.Vfs.fromURL]
     */
    val defaultUrlTypes: MutableList<VfsUrlTypeParser> =
            mutableListOf(VfsUrlTypes::jarFile,
                          VfsUrlTypes::jarStream,
                          VfsUrlTypes::jarUrl,
                          VfsUrlTypes::directory,
                          VfsUrlTypes::jbossVfs,
                          VfsUrlTypes::jbossVfsFile,
                          VfsUrlTypes::bundle)

    /**
     * tries to create a VirtualDir from the given url, using the given urlTypeParsers
     */
    fun fromURL(url: URL,
                fileSystem: FileSystem = FileSystems.getDefault(),
                urlTypeParsers: List<VfsUrlTypeParser> = defaultUrlTypes) =
            urlTypeParsers.mapNotNull { parser -> tryOrNull { parser(url, fileSystem) } }.firstOrNull()
            ?: throw RuntimeException("could not create VirtualDir from url, no matching VfsUrlTypeParser was found [${url.toExternalForm()}]\neither use fromURL(final URL url, final List<VfsUrlTypeParser> urlTypeParsers) or use the static setDefaultURLTypes(final List<VfsUrlTypeParser> urlTypeParsers) or addDefaultURLTypes(VfsUrlTypeParser urlType) with your specialized VfsUrlTypeParser.")

    /**
     * return an iterable of all [VirtualFile] in given urls, matching filePredicate
     */
    fun findFiles(inUrls: Collection<URL>, fileSystem: FileSystem = FileSystems.getDefault()) =
            inUrls.asSequence().flatMap { url ->
                try {
                    fromURL(url = url, fileSystem = fileSystem).files()
                } catch (e: Throwable) {
                    logger.error("could not findFiles for url. continuing. [$url]", e)
                    emptySequence<VirtualFile>()
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

// todo: sanal bir dosya sistemi gibi davranmak yerine java'nın Path ve FileSystems kabiliyetleri kullanılabilir gibi duruyor

abstract class VirtualDir(val path: Path) : Closeable {
    abstract fun files(): Sequence<VirtualFile>
    override fun toString() = path.toString()
}

// todo: convert to data class or something
interface VirtualFile {
    val name: String

    // todo: use Path instead of String
    val relativePath: String?

    fun openInputStream(): InputStream
}

class SystemDir(path: Path) : VirtualDir(path) {
    init {
        if (!path.isDirectory || !path.canRead()) throw RuntimeException("cannot use dir $path")
    }

    override fun files() = when {
        !path.exists() -> emptySequence()
        else           -> path.walkTopDown().filter { !it.isDirectory }.map { SystemFile(this, it) }
    }

    override fun close() = Unit
}

class ZipDir(val jarFile: JarFile, val fileSystem: FileSystem) : VirtualDir(fileSystem.getPath(jarFile.name)) {
    // todo: JarFile'ı file'a bulaşmadan path'ten create edebilmeliyiz: JarInputStream ile oluyormuş
    constructor(path: Path) : this(JarFile(path.toFile()), path.fileSystem)

    override fun files() = jarFile.entries().asSequence().filter { !it.isDirectory }.map {
        ZipFile(this, it, fileSystem)
    }

    override fun close() = tryOrIgnore { jarFile.close() }
}

class JarInputDir(private val url: URL, val fileSystem: FileSystem) : VirtualDir(fileSystem.getPath(url.path)) {
    var jarInputStream: JarInputStream? = null
    var cursor: Long = 0
    var nextCursor: Long = 0

    // todo: bunu sequence olmaktan çıkar yada sequence bitince jar input stream otomatik kapatılsın bunun bir yolunu bul
    override fun files(): Sequence<VirtualFile> {
        jarInputStream =
                tryOrThrow("Could not open url connection: $url") { JarInputStream(url.openConnection().getInputStream()) }
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

class SystemFile(root: SystemDir, private val file: Path) : VirtualFile {
    override val name = file.name
    override val relativePath = file.path.replace("\\", "/").removePrefix("${root.path}/")
    override fun openInputStream() = file.inputStream()
    override fun toString() = file.toString()
}

class ZipFile(private val root: ZipDir, private val entry: ZipEntry, val fileSystem: FileSystem) : VirtualFile {
    override val name = entry.name.substringAfterLast(fileSystem.separator)
    override val relativePath = entry.name
    override fun openInputStream() = root.jarFile.getInputStream(entry)
    override fun toString() = "${root.path}!${fileSystem.separator}$entry"
}

class JarInputFile(entry: ZipEntry,
                   private val dir: JarInputDir,
                   private val from: Long,
                   private val to: Long,
                   fileSystem: FileSystem) : VirtualFile {
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

typealias VfsUrlTypeParser = (url: URL, fileSystem: FileSystem) -> VirtualDir?

object VfsUrlTypes {
    /**
     * creates a [ZipDir] over a jar file
     */
    fun jarFile(url: URL, fileSystem: FileSystem) = mapIf(url.protocol == "file" && url.hasJarFileInPath()) {
        ZipDir(Vfs.getFile(url, fileSystem).throwIfNull("virtualFile from url: $url"))
    }

    /**
     * creates a [JarInputDir] over jar files, using Java's JarInputStream
     */
    fun jarStream(url: URL, fileSystem: FileSystem) =
            mapIf(url.protocol != "file" && url.toExternalForm().contains(".jar")) { JarInputDir(url, fileSystem) }

    /**
     * creates a [ZipDir] over a jar url (contains ".jar!/" in it's name), using Java's [JarURLConnection]
     */
    fun jarUrl(url: URL, fileSystem: FileSystem) = mapIf(url.protocol in listOf("jar", "zip", "wsjar")) {
        tryOrNull {
            (url.openConnection() as? JarURLConnection)?.let { urlConnection ->
                urlConnection.setUseCaches(false)
                ZipDir(urlConnection.jarFile, fileSystem)
            }
        } ?: Vfs.getFile(url, fileSystem)?.let { ZipDir(it) }
    }

    /**
     * creates a [SystemDir] over a file system directory
     */
    fun directory(url: URL, fileSystem: FileSystem) = mapIf(url.protocol == "file" && !url.hasJarFileInPath()) {
        Vfs.getFile(url, fileSystem)?.let { file ->
            mapIf(file.isDirectory) { SystemDir(file) }
        }
    }

    private fun URL.hasJarFileInPath() = toExternalForm().matches(".*\\.jar(!.*|$)".toRegex())

    /**
     * for protocols vfs, using jboss vfs (should be provided in classpath)
     */
    fun jbossVfs(url: URL, fileSystem: FileSystem) = mapIf(url.protocol == "vfs") {
        val content = url.openConnection().content
        val virtualFile = contextClassLoader().throwIfNull("contextClassLoader").loadClass("org.jboss.vfs.VirtualFile")
        // todo: physicalFile'ın direk Path olarak alınması mümkün olabilir mi?
        // todo: Jboss'u test dependency olarak ekleyip bunu test edebiliriz
        val physicalFile = (virtualFile.getMethod("getPhysicalFile").invoke(content) as java.io.File).toPath()
        val name = virtualFile.getMethod("getName").invoke(content) as String
        var file = physicalFile.parent.resolve(name)
        if (!file.exists() || !file.canRead()) file = physicalFile
        if (file.isDirectory) SystemDir(file) else ZipDir(file)
    }

    /**
     * creates a [UrlTypeVFS] for protocols vfszip and vfsfile.
     */
    fun jbossVfsFile(url: URL, fileSystem: FileSystem) = tryOrNull {
        when (url.protocol) {
            "vfsfile" -> ZipDir(JarFile(URL(url.toString().replace("vfsfile", "file")).file), fileSystem)
            "vfszip"  -> {
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
                            ret = URL(when {
                                          zipPath.trim { it <= ' ' }.isEmpty() -> "$prefix/$zipFile"
                                          else                                 -> "$prefix/$zipFile!$zipPath"
                                      })
                            break
                        }
                    }
                }
                val adaptURL = ret ?: throw RuntimeException("Unable to identify the real zip file in path '$path'.")
                ZipDir(JarFile(adaptURL.file), fileSystem)
            }
            else      -> null
        }
    }

    /**
     * for bundle protocol, using eclipse FileLocator (should be provided in classpath)
     */
    fun bundle(url: URL, fileSystem: FileSystem) = mapIf(url.protocol.startsWith("bundle")) {
        Vfs.fromURL(url = contextClassLoader().throwIfNull("contextClassLoader").loadClass("org.eclipse.core.runtime.FileLocator").getMethod(
                "resolve",
                URL::class.java).invoke(null, url) as URL, fileSystem = fileSystem)
    }
}
