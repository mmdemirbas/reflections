package org.reflections

import java.util.*

class Multimap<K, V> {
    val map = mutableMapOf<K, MutableSet<V>>()

    val isEmpty get() = map.isEmpty()

    // Query Operations

    fun size(): Int = map.values.map { it.size }.sum()

    // Modification Operations

    fun put(key: K, value: V) = getOrPut(key).add(value)

    // Bulk Operations

    fun putAll(multimap: Multimap<out K, out V>): Boolean {
        multimap.entries().forEach { put(it.key, it.value) }
        return true
    }

    private fun getOrPut(key: K) = map.getOrPut(key) { mutableSetOf() }

    // Views

    fun get(key: K) = map[key]

    fun keySet() = map.keys

    fun values() = map.values.flatten()

    fun entries(): Collection<Map.Entry<K, V>> = map.entries.flatMap { (key, values) ->
        values.map { value ->
            AbstractMap.SimpleImmutableEntry(key, value)
        }
    }

    fun asMap() = map
}