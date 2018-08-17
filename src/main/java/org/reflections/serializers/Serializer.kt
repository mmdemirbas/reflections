package org.reflections.serializers

import org.reflections.Reflections

import java.io.File
import java.io.InputStream

/**
 * Seriliazer of a [org.reflections.Reflections] instance
 */
interface Serializer {

    /**
     * reads the input stream into a new Reflections instance, populating it's store
     */
    fun read(inputStream: InputStream): Reflections

    /**
     * saves a Reflections instance into the given filename
     */
    fun save(reflections: Reflections, filename: String): File

    /**
     * returns a string serialization of the given Reflections instance
     */
    fun toString(reflections: Reflections): String
}
