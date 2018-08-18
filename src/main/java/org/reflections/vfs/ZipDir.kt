package org.reflections.vfs

import org.reflections.Reflections
import org.reflections.vfs.Vfs.Dir
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
        get() = jarFile.name

    override val files
        get() = jarFile.entries().asSequence().filter { !it.isDirectory }.map {
            ZipFile(this, it)
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
