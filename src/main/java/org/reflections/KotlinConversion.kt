package org.reflections

import javassist.bytecode.ClassFile
import javassist.bytecode.FieldInfo
import javassist.bytecode.MethodInfo
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.util.*
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger


interface ClassWrapper
interface FieldWrapper
interface MethodWrapper


data class JavassistClassWrapper(val delegate: ClassFile) : ClassWrapper
data class JavassistFieldWrapper(val delegate: FieldInfo) : FieldWrapper
data class JavassistMethodWrapper(val delegate: MethodInfo) : MethodWrapper


data class JavaReflectionClassWrapper(val delegate: Class<*>) : ClassWrapper
data class JavaReflectionFieldWrapper(val delegate: Field) : FieldWrapper
data class JavaReflectionMethodWrapper(val delegate: Member) : MethodWrapper


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

    private fun getOrPut(key: K) = map.getOrPut(key, { mutableSetOf() })

    // Views

    fun get(key: K) = map.get(key)

    fun keySet() = map.keys

    fun values() = map.values.flatten()

    fun entries(): Collection<Map.Entry<K, V>> = map.entries.flatMap { (key, values) ->
        values.map { value ->
            AbstractMap.SimpleImmutableEntry(key, value)
        }
    }

    fun asMap() = map
}

open class DefaultThreadFactory(val namePrefix: String, val daemon: Boolean) : ThreadFactory {
    private val group = System.getSecurityManager()?.threadGroup ?: Thread.currentThread().threadGroup
    private val threadNumber = AtomicInteger(1)

    override fun newThread(r: Runnable) = Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0).apply {
        isDaemon = daemon
        priority = Thread.NORM_PRIORITY
    }
}
