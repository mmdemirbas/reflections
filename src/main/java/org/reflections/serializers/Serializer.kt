package org.reflections.serializers

import org.reflections.scanners.CompositeScanner
import org.reflections.util.makeParents
import org.reflections.util.tryOrThrow
import java.io.File
import java.io.FileWriter
import java.io.Reader
import java.lang.Appendable

/**
 * Serializer of a [CompositeScanner] instances
 */
interface Serializer {
    fun read(reader: Reader): CompositeScanner
    fun write(scanners: CompositeScanner, writer: Appendable)

    fun save(scanners: CompositeScanner, file: File) = tryOrThrow("could not save to file $file") {
        file.makeParents()
        FileWriter(file).use { writer -> XmlSerializer.write(scanners, writer) }
    }

    fun toString(scanners: CompositeScanner) = StringBuilder().apply { write(scanners, this) }.toString()
}
