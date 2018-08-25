package org.reflections

import org.reflections.scanners.Scanner
import org.reflections.util.IndexKey
import org.reflections.util.Multimap
import org.reflections.util.indexName
import org.reflections.util.indexType
import kotlin.reflect.KClass

class Stores {
    private val indices = mutableMapOf<KClass<out Scanner>, Multimap<IndexKey, IndexKey>>()

    fun getOrThrowRecursively(index: KClass<out Scanner>, keys: Collection<IndexKey>): Collection<IndexKey> {
        val found = getOrThrow(index, keys)
        return when {
            found.isEmpty() -> found
            else            -> found + getOrThrowRecursively(index, found)
        }
    }

    fun getOrThrow(indexKey: KClass<out Scanner>, keys: Collection<IndexKey>) =
            keys.flatMap { key -> getOrThrow(indexKey).get(key).orEmpty() }

    fun getOrThrow(index: KClass<out Scanner>) =
            getOrNull(index) ?: throw ReflectionsException("Scanner ${index.indexName()} was not configured")

    fun getOrNull(index: KClass<out Scanner>) = indices[index]
    fun getOrCreate(index: String) = getOrCreate(index.indexType())
    fun getOrCreate(index: KClass<out Scanner>) = indices.getOrPut(index) { Multimap() }
    fun keys() = indices.keys
    fun entries() = indices.entries

    fun triples() = indices.flatMap { (scanner, multimap) ->
        multimap.entries().map { (k, v) ->
            Triple(scanner, k, v)
        }
    }
}
