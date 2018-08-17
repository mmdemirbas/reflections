package org.reflections.vfs

import com.google.common.collect.AbstractIterator
import org.reflections.ReflectionsException
import org.reflections.util.Utils
import org.reflections.vfs.Vfs.Dir
import org.reflections.vfs.Vfs.File
import java.io.IOException
import java.net.URL
import java.util.jar.JarInputStream

/**
 *
 */
class JarInputDir(private val url: URL) : Dir {

    var jarInputStream: JarInputStream? = null
    var cursor: Long = 0
    var nextCursor: Long = 0

    override val path: String
        get() = url.path

    override val files: Iterable<File>
        get() = Iterable { newIterator() }

    private inner class newIterator : AbstractIterator<File>() {

        init {
            try {
                jarInputStream = JarInputStream(url.openConnection().getInputStream())
            } catch (e: Exception) {
                throw ReflectionsException("Could not open url connection", e)
            }

        }

        override fun computeNext(): File? {
            while (true) {
                try {
                    val entry = jarInputStream?.nextJarEntry ?: return endOfData()

                    var size = entry.size
                    if (size < 0) {
                        size = 0xffffffffL + size //JDK-6916399
                    }
                    nextCursor += size
                    if (!entry.isDirectory) {
                        return JarInputFile(entry, this@JarInputDir, cursor, nextCursor)
                    }
                } catch (e: IOException) {
                    throw ReflectionsException("could not get next zip entry", e)
                }

            }
        }
    }

    override fun close() {
        Utils.close(jarInputStream)
    }
}
