package org.reflections.vfs

import org.reflections.util.contextClassLoader
import org.reflections.util.logWarn
import org.reflections.util.tryOrDefault
import java.io.File
import java.net.JarURLConnection
import java.net.URL
import java.util.jar.JarFile
import java.util.regex.Pattern

interface VfsUrlType {
    // todo: matches metodu diğerinin içine katılabilir. null dönmüşse match etmemiş demektir.
    fun matches(url: URL): Boolean
    fun createDir(url: URL): VfsDir?
}

/**
 * built-in url types used by [org.reflections.vfs.Vfs.fromURL]
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
enum class BuiltinVfsUrlTypes : VfsUrlType {
    jarFile {
        override fun matches(url: URL) = url.protocol == "file" && url.hasJarFileInPath()
        override fun createDir(url: URL) = ZipDir(JarFile(Vfs.getFile(url)!!))
    },

    jarUrl {
        override fun matches(url: URL) = ("jar" == url.protocol || "zip" == url.protocol || "wsjar" == url.protocol)

        override fun createDir(url: URL): VfsDir? {
            try {
                val urlConnection = url.openConnection()
                if (urlConnection is JarURLConnection) {
                    urlConnection.setUseCaches(false)
                    return ZipDir(urlConnection.jarFile)
                }
            } catch (e: Throwable) { /*fallback*/
            }

            val file = Vfs.getFile(url)
            return if (file == null) null else ZipDir(JarFile(file))
        }
    },

    directory {
        override fun matches(url: URL) = when {
            url.protocol == "file" && !url.hasJarFileInPath() -> Vfs.getFile(url)?.isDirectory == true
            else                                              -> false
        }

        override fun createDir(url: URL) = SystemDir(Vfs.getFile(url)!!)
    },

    jboss_vfs {
        override fun matches(url: URL) = url.protocol == "vfs"

        override fun createDir(url: URL): VfsDir? {
            val content = url.openConnection().content
            val virtualFile = contextClassLoader()!!.loadClass("org.jboss.vfs.VirtualFile")
            val physicalFile = virtualFile.getMethod("getPhysicalFile").invoke(content) as java.io.File
            val name = virtualFile.getMethod("getName").invoke(content) as String
            var file = java.io.File(physicalFile.parentFile, name)
            if (!file.exists() || !file.canRead()) file = physicalFile
            return if (file.isDirectory) SystemDir(file) else ZipDir(JarFile(file))
        }
    },

    jboss_vfsfile {
        private val VFSZIP = "vfszip"
        private val VFSFILE = "vfsfile"

        override fun matches(url: URL) = VFSZIP == url.protocol || VFSFILE == url.protocol

        override fun createDir(url: URL) = try {
            ZipDir(JarFile(adaptURL(url).file))
        } catch (e: Exception) {
            logWarn("Could not get URL", e)
            null
        }

        private fun adaptURL(url: URL) = tryOrDefault(url) {
            when (url.protocol) {
                VFSFILE -> return@tryOrDefault URL(url.toString().replace(VFSFILE, "file"))
                VFSZIP  -> {
                    val path = url.path
                    var pos = 0
                    while (pos != -1) {
                        val matcher = Pattern.compile("\\.[ejprw]ar/").matcher(path)
                        pos = if (matcher.find(pos)) matcher.end() else -1

                        if (pos > 0) {
                            val file = File(path.substring(0, pos - 1))
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
                                return@tryOrDefault when {
                                    zipPath.trim { it <= ' ' }.isEmpty() -> URL("$prefix/$zipFile")
                                    else                                 -> URL("$prefix/$zipFile!$zipPath")
                                }
                            }
                        }
                    }
                    throw RuntimeException("Unable to identify the real zip file in path '$path'.")
                }
                else    -> return@tryOrDefault url
            }
        }
    },

    bundle {
        override fun matches(url: URL) = url.protocol.startsWith("bundle")

        override fun createDir(url: URL) =
                Vfs.fromURL(contextClassLoader()!!.loadClass("org.eclipse.core.runtime.FileLocator").getMethod("resolve",
                                                                                                               URL::class.java).invoke(
                        null,
                        url) as URL)
    },

    jarInputStream {
        override fun matches(url: URL) = url.toExternalForm().contains(".jar")
        override fun createDir(url: URL) = JarInputDir(url)
    }
}

private fun URL.hasJarFileInPath() = toExternalForm().matches(".*\\.jar(!.*|$)".toRegex())
