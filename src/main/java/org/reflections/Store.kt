package org.reflections

import java.util.*

/**
 * stores metadata information in multimaps
 *
 * use the different query methods (getXXX) to query the metadata
 *
 * the query methods are string based, and does not cause the class loader to define the types
 *
 * use [org.reflections.Reflections.store] to access this store
 */

class Store {

    private val storeMap = mutableMapOf<String, Multimap<String, String>>()

    /**
     * return all indices
     */
    fun keySet(): Set<String> = storeMap.keys

    /**
     * get or create the multimap object for the given `index`
     */
    fun getOrCreate(index: String) = storeMap.getOrPut(index) { Multimap() }

    /**
     * get the multimap object for the given `index`, otherwise throws a [org.reflections.ReflectionsException]
     */
    operator fun get(index: String) = storeMap[index] ?: throw ReflectionsException("Scanner $index was not configured")

    /**
     * get the values stored for the given `index` and `keys`
     */
    operator fun get(index: String, vararg keys: String) = get(index, Arrays.asList(*keys))

    /**
     * get the values stored for the given `index` and `keys`
     */
    operator fun get(index: String, keys: Iterable<String>): Iterable<String> {
        val mmap = get(index)
        val result = mutableListOf<String>()
        for (key in keys) {
            result.addAll(mmap.get(key).orEmpty())
        }
        return result
    }

    /**
     * recursively get the values stored for the given `index` and `keys`, including keys
     */
    private fun getAllIncluding(index: String, keys: Iterable<String>, result: MutableList<String>): Iterable<String> {
        result.addAll(keys)
        for (key in keys) {
            val values = get(index, key)
            if (values.iterator().hasNext()) {
                getAllIncluding(index, values, result)
            }
        }
        return result
    }

    /**
     * recursively get the values stored for the given `index` and `keys`, not including keys
     */
    fun getAll(index: String, key: String) = getAllIncluding(index, get(index, key), mutableListOf())

    /**
     * recursively get the values stored for the given `index` and `keys`, not including keys
     */
    fun getAll(index: String, keys: Iterable<String>) = getAllIncluding(index, get(index, keys), mutableListOf())
}
