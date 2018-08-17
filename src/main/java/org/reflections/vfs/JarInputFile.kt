package org.reflections.vfs

import org.reflections.vfs.Vfs.File

import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry

/**
 *
 */
class JarInputFile(private val entry: ZipEntry,
                   private val jarInputDir: JarInputDir,
                   private val fromIndex: Long,
                   private val endIndex: Long) : File {

    override val name: String
        get() {
            val name = entry.name
            return name.substring(name.lastIndexOf('/') + 1)
        }

    override val relativePath: String
        get() = entry.name

    override fun openInputStream(): InputStream {
        return object : InputStream() {
            @Throws(IOException::class)
            override fun read(): Int {
                if (jarInputDir.cursor >= fromIndex && jarInputDir.cursor <= endIndex) {
                    val read = jarInputDir.jarInputStream!!.read()
                    jarInputDir.cursor++
                    return read
                } else {
                    return -1
                }
            }
        }
    }
}
