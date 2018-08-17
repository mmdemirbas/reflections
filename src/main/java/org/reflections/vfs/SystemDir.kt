package org.reflections.vfs

import com.google.common.collect.AbstractIterator
import com.google.common.collect.Lists
import org.reflections.vfs.Vfs.Dir

import java.io.File
import java.util.*

/*
* An implementation of {@link org.reflections.vfs.Vfs.Dir} for directory {@link java.io.File}.
*/
class SystemDir(private val file: File?) : Dir {

    init {
        if ((file != null) && (!file!!.isDirectory() || !file!!.canRead())) {
            throw RuntimeException("cannot use dir " + file!!)
        }
    }

    override val path: String
        get() {
            if (file == null) {
                return "/NO-SUCH-DIRECTORY/"
            }
            return file!!.getPath().replace("\\", "/")
        }
    override val files: Iterable<Vfs.File>
        get() {
            if ((file == null) || !file!!.exists()) {
                return emptyList<Vfs.File>()
            }
            return Iterable {
                object : AbstractIterator<Vfs.File>() {
                    val stack = Stack<File>()

                    init {
                        stack.addAll(listFiles(file!!))
                    }

                    protected override fun computeNext(): Vfs.File? {
                        while (!stack.isEmpty()) {
                            val file = stack.pop()
                            if (file.isDirectory()) {
                                stack.addAll(listFiles(file))
                            } else {
                                return SystemFile(this@SystemDir, file)
                            }
                        }

                        return endOfData()
                    }
                }
            }
        }

    private fun listFiles(file: File): List<File> {
        val files = file.listFiles()

        return if ((files != null)) Lists.newArrayList<File>(*files!!) else Lists.newArrayList<File>()
    }

    public override fun close() {}

    public override fun toString(): String {
        return path
    }
}
