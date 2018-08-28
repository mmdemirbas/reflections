package org.reflections.serializers

import org.reflections.Configuration
import java.io.File
import java.io.InputStream

/**
 * Serializer of a [org.reflections.Configuration] instance
 */
interface Serializer {
    fun read(inputStream: InputStream): Configuration
    fun save(configuration: Configuration, file: File)
    fun toString(configuration: Configuration): String
}
