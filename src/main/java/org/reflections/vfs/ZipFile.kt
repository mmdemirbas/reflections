package org.reflections.vfs

import org.reflections.vfs.Vfs.File

import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry

/**
 * an implementation of [File] for [java.util.zip.ZipEntry]
 */
class ZipFile(private val root: ZipDir, private val entry: ZipEntry) : File {

    override val name: String
        get() {
            val name = entry.name
            return name.substring(name.lastIndexOf('/') + 1)
        }

    override val relativePath: String?
        get() {
            return entry.name
        }

    @Throws(IOException::class)
    override fun openInputStream(): InputStream {
        return root.jarFile.getInputStream(entry)
    }

    override fun toString(): String {
        return root.path + '!'.toString() + java.io.File.separatorChar + entry
    }
}
