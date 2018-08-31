package org.reflections.serializers

import org.reflections.scanners.SimpleScanner
import java.io.File
import java.io.InputStream

/**
 * Serializer of a [SimpleScanner] instances
 */
interface Serializer {
    fun read(inputStream: InputStream): List<SimpleScanner<*>>

    // todo: remove save method or reduce to a single common method
    fun save(scanners: List<SimpleScanner<*>>, file: File)

    // todo: refactor to give output to a output stream etc. for efficiency. toString still can remain as a common utility.
    fun toString(scanners: List<SimpleScanner<*>>): String
}
