package org.reflections.vfs

import org.reflections.vfs.Vfs.Dir
import java.io.File

/*
* An implementation of {@link org.reflections.vfs.Vfs.Dir} for directory {@link java.io.File}.
*/
class SystemDir(private val file: File?) : Dir {

    init {
        if ((file != null) && (!file!!.isDirectory || !file!!.canRead())) {
            throw RuntimeException("cannot use dir " + file!!)
        }
    }

    override val path: String
        get() {
            if (file == null) {
                return "/NO-SUCH-DIRECTORY/"
            }
            return file.path.replace("\\", "/")
        }
    override val files
        get() = when {
            file == null || !file.exists() -> emptySequence()
            else                           -> file.walkTopDown().filter { !it.isDirectory }.map { SystemFile(this, it) }
        }

    override fun close() {}

    override fun toString(): String {
        return path
    }
}
