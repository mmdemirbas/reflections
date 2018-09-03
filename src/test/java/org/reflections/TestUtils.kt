package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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

// todo: userDir hack midir nedir buna gerek var mı?
//a hack to fix user.dir issue(?) in surfire
val userDir: Path
    get() {
        // todo: fileSystem parametric verilmeli
        val file = Paths.get(System.getProperty("user.dir"))
        return when {
            Files.list(file).anyMatch { it.toString() == "reflections" } -> file.resolve("reflections")
            else                                                         -> file
        }
    }