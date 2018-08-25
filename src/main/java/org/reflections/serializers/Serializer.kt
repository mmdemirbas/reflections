package org.reflections.serializers

import org.reflections.Reflections

import java.io.File
import java.io.InputStream

/**
 * Seriliazer of a [org.reflections.Reflections] instance
 */
interface Serializer {
    fun read(inputStream: InputStream): Reflections
    fun save(reflections: Reflections, filename: String): File
    fun toString(reflections: Reflections): String
}
