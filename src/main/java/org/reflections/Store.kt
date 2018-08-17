package org.reflections

import com.google.common.collect.*

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * stores metadata information in multimaps
 *
 * use the different query methods (getXXX) to query the metadata
 *
 * the query methods are string based, and does not cause the class loader to define the types
 *
 * use [org.reflections.Reflections.getStore] to access this store
 */
class Store {

    @Transient private val concurrent: Boolean
    private val storeMap: MutableMap<String, Multimap<String, String>>

    //used via reflection
    protected constructor() {
        storeMap = HashMap()
        concurrent = false
    }

    constructor(configuration: Configuration) {
        storeMap = HashMap()
        concurrent = configuration.executorService != null
    }

    /**
     * return all indices
     */
    fun keySet(): Set<String> {
        return storeMap.keys
    }

    /**
     * get or create the multimap object for the given `index`
     */
    fun getOrCreate(index: String): Multimap<String, String> {
        var mmap: Multimap<String, String>? = storeMap[index]
        if (mmap == null) {
            val multimap = Multimaps.newSetMultimap(HashMap<String, Collection<String>>()) {
                Sets.newSetFromMap(ConcurrentHashMap())
            }
            mmap = if (concurrent) Multimaps.synchronizedSetMultimap(multimap) else multimap
            storeMap[index] = mmap
        }
        return mmap!!
    }

    /**
     * get the multimap object for the given `index`, otherwise throws a [org.reflections.ReflectionsException]
     */
    operator fun get(index: String): Multimap<String, String> {
        return storeMap[index] ?: throw ReflectionsException("Scanner $index was not configured")
    }

    /**
     * get the values stored for the given `index` and `keys`
     */
    operator fun get(index: String, vararg keys: String): Iterable<String> {
        return get(index, Arrays.asList(*keys))
    }

    /**
     * get the values stored for the given `index` and `keys`
     */
    operator fun get(index: String, keys: Iterable<String>): Iterable<String> {
        val mmap = get(index)
        val result = IterableChain<String>()
        for (key in keys) {
            result.addAll(mmap.get(key))
        }
        return result
    }

    /**
     * recursively get the values stored for the given `index` and `keys`, including keys
     */
    private fun getAllIncluding(index: String,
                                keys: Iterable<String>,
                                result: IterableChain<String>): Iterable<String> {
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
    fun getAll(index: String, key: String): Iterable<String> {
        return getAllIncluding(index, get(index, key), IterableChain())
    }

    /**
     * recursively get the values stored for the given `index` and `keys`, not including keys
     */
    fun getAll(index: String, keys: Iterable<String>): Iterable<String> {
        return getAllIncluding(index, get(index, keys), IterableChain())
    }

    class IterableChain<T> : Iterable<T> {

        val chain = Lists.newArrayList<Iterable<T>>()

        fun addAll(iterable: Iterable<T>) {
            chain.add(iterable)
        }

        override fun iterator(): Iterator<T> {
            return Iterables.concat(chain).iterator()
        }
    }
}
