package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.reflections.util.classAndInterfaceHieararchyExceptObject
import java.io.File
import java.lang.reflect.Executable
import java.lang.reflect.Member

/**
 * Asserts that [expected] and [actual] are equal after each item transformed to string,
 * and sorted by [sortBy] function if provided.
 *
 * If necessary, the failure message will be retrieved lazily from the supplied [message] function.
 */
fun <T> assertToStringEqualsSorted(expected: Iterable<T>, actual: Iterable<T>, message: () -> String? = { null }) =
        assertToStringEquals(expected, actual, message, Any?::toString)

/**
 * Asserts that [expected] and [actual] are equal after each item transformed to string,
 * and sorted by [sortBy] function if provided.
 *
 * If necessary, the failure message will be retrieved lazily from the supplied [message] function.
 */
fun <T, R : Comparable<R>> assertToStringEquals(expected: Iterable<T>,
                                                actual: Iterable<T>,
                                                message: () -> String? = { null },
                                                sortBy: ((T) -> R?)?) {
    assertTransformedEquals(expected, actual, message) {
        val sorted = when (sortBy) {
            null -> it
            else -> it.sortedBy(sortBy)
        }
        sorted.joinToString("\n")
    }
}

/**
 * Asserts that [expected] and [actual] are equal after [transform]ation.
 *
 * If necessary, the failure message will be retrieved lazily from the supplied [message] function.
 */
fun <T, R> assertTransformedEquals(expected: T, actual: T, message: () -> String? = { null }, transform: (T) -> R) =
        assertEquals(transform(expected), transform(actual), message)


fun Class<*>.allMethods() = neededHierarchy().flatMap { it.declaredMethods.filterNotNull() }
fun Class<*>.allFields() = neededHierarchy().flatMap { it.declaredFields.filterNotNull() }
fun Class<*>.allAnnotations() = neededHierarchy().flatMap { it.declaredAnnotations.filterNotNull() }
fun Class<*>.neededHierarchy() = classAndInterfaceHieararchyExceptObject()

fun Member.hasParamTypes(types: List<Class<*>>) = parameterTypes() == types
fun Member.hasParamTypesSubtypesOf(types: List<Class<*>>) = isAssignable(parameterTypes(), types)
fun Member.hasParamTypesSupertypesOf(types: List<Class<*>>) = isAssignable(types, parameterTypes())
fun Member.hasModifier(mod: Int): Boolean = modifiers and mod != 0
fun Member.parameterTypes() = (this as? Executable)?.parameterTypes.orEmpty().asList()

fun isAssignable(child: List<Class<*>>, parent: List<Class<*>>): Boolean {
    return when {
        child.size != parent.size -> false
        else                      -> {
            child.indices.forEach { i ->
                if (!parent[i].isAssignableFrom(child[i]) || parent[i] == Any::class.java && child[i] != Any::class.java) {
                    return false
                }
            }
            true
        }
    }
}

//a hack to fix user.dir issue(?) in surfire
val userDir: File
    get() {
        var file = File(System.getProperty("user.dir"))
        if (listOf(*file.list()!!).contains("reflections")) {
            file = File(file, "reflections")
        }
        return file
    }