package org.reflections.vfs

import com.google.common.collect.AbstractIterator
import org.reflections.Reflections
import org.reflections.vfs.Vfs.Dir
import org.reflections.vfs.Vfs.File
import java.io.IOException
import java.util.jar.JarFile

/**
 * an implementation of [Dir] for [java.util.zip.ZipFile]
 */
class ZipDir(jarFile: JarFile) : Dir {

    internal val jarFile: java.util.zip.ZipFile

    init {
        this.jarFile = jarFile
    }

    override val path: String
        get() {
            return jarFile.name
        }
    override val files: Iterable<File>
        get() {
            return Iterable {
                object : AbstractIterator<File>() {
                    internal val entries = jarFile.entries()

                    override fun computeNext(): File? {
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.isDirectory) {
                                return ZipFile(this@ZipDir, entry)
                            }
                        }

                        return endOfData()
                    }
                }
            }
        }

    override fun close() {
        try {
            jarFile.close()
        } catch (e: IOException) {
            if (Reflections.log != null) {
                Reflections.log.warn("Could not close JarFile", e)
            }
        }

    }

    override fun toString(): String {
        return jarFile.name
    }
}
