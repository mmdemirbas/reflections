package org.reflections.serializers

import org.reflections.Reflections

import java.io.File
import java.io.InputStream

/**
 * Serializer of a [org.reflections.Reflections] instance
 */
interface Serializer {
    fun read(inputStream: InputStream): Reflections
    fun save(reflections: Reflections, file: File)
    fun toString(reflections: Reflections): String
}
