package org.reflections.vfs

import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * an implementation of [org.reflections.vfs.Vfs.File] for a directory [java.io.File]
 */
class SystemFile(private val root: SystemDir, private val file: java.io.File) : Vfs.File {

    override val name: String
        get() {
            return file.name
        }
    override val relativePath: String?
        get() {
            val filepath = file.path.replace("\\", "/")
            return if (filepath.startsWith(root.path)) {
                filepath.substring(root.path.length + 1)
            } else null

            //should not get here
        }

    override fun openInputStream(): InputStream {
        try {
            return FileInputStream(file)
        } catch (e: FileNotFoundException) {
            throw RuntimeException(e)
        }

    }

    override fun toString(): String {
        return file.toString()
    }
}
