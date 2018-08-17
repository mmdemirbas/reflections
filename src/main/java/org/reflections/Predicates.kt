package org.reflections

/**
 * @author Muhammed Demirbaş
 * @since 2018-08-17 15:36
 */
object Predicates {

    @JvmStatic
    fun <T> `in`(collection: Collection<*>): (T) -> Boolean {
        return { it -> collection.contains(it) }
    }

    @JvmStatic
    fun <T> `not`(predicate: (T) -> Boolean): (T) -> Boolean {
        return { it -> !predicate(it) }
    }
}