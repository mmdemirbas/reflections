package org.reflections.serializers

import org.reflections.Scanners
import java.io.File
import java.io.InputStream

/**
 * Serializer of a [org.reflections.Scanners] instance
 */
interface Serializer {
    fun read(inputStream: InputStream): Scanners
    fun save(scanners: Scanners, file: File)
    fun toString(scanners: Scanners): String
}
