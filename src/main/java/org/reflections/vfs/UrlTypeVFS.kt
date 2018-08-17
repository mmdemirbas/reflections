package org.reflections.vfs

import org.reflections.Reflections
import org.reflections.ReflectionsException
import org.reflections.vfs.Vfs.Dir
import org.reflections.vfs.Vfs.UrlType
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.jar.JarFile
import java.util.regex.Pattern

/**
 * UrlType to be used by Reflections library.
 * This class handles the vfszip and vfsfile protocol of JBOSS files.
 *
 *
 *
 * to use it, register it in Vfs via [org.reflections.vfs.Vfs.addDefaultURLTypes] or [org.reflections.vfs.Vfs.setDefaultURLTypes].
 *
 * @author Sergio Pola
 */
class UrlTypeVFS : UrlType {

    private val realFile = { file: File -> file.exists() && file.isFile() }

    override fun matches(url: URL): Boolean {
        return VFSZIP == url.protocol || VFSFILE == url.protocol
    }

    override fun createDir(url: URL): Dir? {
        try {
            val adaptedUrl = adaptURL(url)
            return ZipDir(JarFile(adaptedUrl.file))
        } catch (e: Exception) {
            try {
                return ZipDir(JarFile(url.file))
            } catch (e1: IOException) {
                if (Reflections.log != null) {
                    Reflections.log.warn("Could not get URL", e)
                    Reflections.log.warn("Could not get URL", e1)
                }
            }

        }

        return null
    }

    @Throws(MalformedURLException::class)
    private fun adaptURL(url: URL): URL {
        return if (VFSZIP == url.protocol) {
            replaceZipSeparators(url.path, realFile)
        } else if (VFSFILE == url.protocol) {
            URL(url.toString().replace(VFSFILE, "file"))
        } else {
            url
        }
    }

    companion object {

        private val REPLACE_EXTENSION = arrayOf(".ear/", ".jar/", ".war/", ".sar/", ".har/", ".par/")

        private val VFSZIP = "vfszip"
        private val VFSFILE = "vfsfile"

        @Throws(MalformedURLException::class)
        private fun replaceZipSeparators(path: String, acceptFile: (File) -> Boolean): URL {
            var pos = 0
            while (pos != -1) {
                pos = findFirstMatchOfDeployableExtention(path, pos)

                if (pos > 0) {
                    val file = File(path.substring(0, pos - 1))
                    if (acceptFile(file)) {
                        return replaceZipSeparatorStartingFrom(path, pos)
                    }
                }
            }

            throw ReflectionsException("Unable to identify the real zip file in path '$path'.")
        }

        private fun findFirstMatchOfDeployableExtention(path: String, pos: Int): Int {
            val p = Pattern.compile("\\.[ejprw]ar/")
            val m = p.matcher(path)
            return if (m.find(pos)) m.end() else -1
        }

        @Throws(MalformedURLException::class)
        private fun replaceZipSeparatorStartingFrom(path: String, pos: Int): URL {
            val zipFile = path.substring(0, pos - 1)
            var zipPath = path.substring(pos)

            var numSubs = 1
            for (ext in REPLACE_EXTENSION) {
                while (zipPath.contains(ext)) {
                    zipPath = zipPath.replace(ext, ext.substring(0, 4) + '!')
                    numSubs++
                }
            }

            val prefix = StringBuilder()
            for (i in 0 until numSubs) {
                prefix.append("zip:")
            }

            return if (zipPath.trim { it <= ' ' }.isEmpty()) URL(prefix.toString() + '/'.toString() + zipFile)
            else URL(prefix.toString() + '/'.toString() + zipFile + '!'.toString() + zipPath)
        }
    }
}
