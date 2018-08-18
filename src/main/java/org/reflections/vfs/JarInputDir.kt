package org.reflections.vfs

import org.reflections.ReflectionsException
import org.reflections.util.Utils
import org.reflections.vfs.Vfs.Dir
import org.reflections.vfs.Vfs.File
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

    override val files: Sequence<File>
        get() {
            try {
                jarInputStream = JarInputStream(url.openConnection().getInputStream())
            } catch (e: Exception) {
                throw ReflectionsException("Could not open url connection", e)
            }

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

    private fun <T> whileNotNull(fn: () -> T?): Sequence<T> {
        val result = mutableListOf<T>()
        var value = fn()
        while (value != null) {
            result += value
            value = fn()
        }
        return result.asSequence()
    }

    override fun close() {
        Utils.close(jarInputStream)
    }
}
