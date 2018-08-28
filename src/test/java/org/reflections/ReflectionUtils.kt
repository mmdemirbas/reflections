package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.reflections.util.classAndInterfaceHieararchyExceptObject
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Member

/**
 * convenient java reflection helper methods
 *
 *
 * 1. some helper methods to get type by name: [classForName] and [classesForNames]
 *
 *
 * 2. some helper methods to get all types/methods/fields/constructors/properties matching some predicates, generally:
 * ``` Set&#60?> result = getAllXXX(type/s, withYYY) ```
 *
 * where get methods are:
 *
 *  * [classAndInterfaceHieararchyExceptObject]
 *  * [getAllFields]
 *  * [getAllMethods]
 *  * [getAllConstructors]
 *
 *
 * and predicates included here all starts with "with", such as
 *
 *  * [withAnnotation]
 *  * [withModifier]
 *  * [withName]
 *  * [withParameters]
 *  * [withAnyParameterAnnotation]
 *  * [withParametersAssignableTo]
 *  * [withParametersAssignableFrom]
 *  * [withPrefix]
 *  * [withReturnType]
 *  * [withType]
 *  * [withTypeAssignableTo]
 *
 *
 *
 * <br></br>
 * for example, getting all getters would be:
 * ```
 * Set&#60Method> getters = getAllMethods(someClasses,
 * Predicates.and(
 * withModifier(Modifier.PUBLIC),
 * withPrefix("get"),
 * withParametersCount(0)));
 * ```
 */
object ReflectionUtils {

    /**
     * get all methods of given `type`, up the super class hierarchy, optionally filtered by `predicates`
     */
    fun getAllMethods(type: Class<*>) =
            type.classAndInterfaceHieararchyExceptObject().flatMap { it.declaredMethods.filterNotNull() }.toSet()

    /**
     * get all fields of given `type`, up the super class hierarchy, optionally filtered by `predicates`
     */
    fun getAllFields(type: Class<*>, predicates: (Field) -> Boolean): Set<Field> =
            type.classAndInterfaceHieararchyExceptObject().flatMap { t -> getFields(t, predicates) }.toSet()

    /**
     * get fields of given `type`, optionally filtered by `predicates`
     */
    fun getFields(type: Class<*>, predicate: (Field) -> Boolean = { true }) =
            type.declaredFields.filter(predicate).toSet()

    /**
     * get all annotations of given `type`, up the super class hierarchy, optionally filtered by `predicates`
     */
    fun <T : AnnotatedElement> getAllAnnotations(type: T) = when (type) {
        is Class<*> -> (type as Class<*>).classAndInterfaceHieararchyExceptObject().flatMap { getAnnotations(it) }.toSet()
        else        -> getAnnotations(type)
    }

    /**
     * get annotations of given `type`, optionally honorInherited, optionally filtered by `predicates`
     */
    fun <T : AnnotatedElement> getAnnotations(type: T, predicate: (Annotation) -> Boolean = { true }) =
            type.declaredAnnotations.filter(predicate).toSet()

    //predicates

    fun withParametersAssignableTo(it: Member, types: List<Class<*>>) = isAssignable(types, parameterTypes(it))

    fun withParameters(it: Member, types: List<Class<*>>) = parameterTypes(it) == types


    fun withParametersAssignableFrom(it: Member, types: List<Class<*>>) = isAssignable(parameterTypes(it), types)

    fun <T : Member> withModifier(it: T, mod: Int): Boolean = it.modifiers and mod != 0

    fun parameterTypes(member: Member?) = (member as? Executable)?.parameterTypes.orEmpty().asList()

    fun isAssignable(childClasses: List<Class<*>>, parentClasses: List<Class<*>>) = when {
        childClasses.size != parentClasses.size -> false
        else                                    -> {
            childClasses.indices.forEach { i ->
                if (!parentClasses[i].isAssignableFrom(childClasses[i]) || parentClasses[i] == Any::class.java && childClasses[i] != Any::class.java) {
                    return false
                }
            }
            true
        }
    }
}


/**
 * Asserts that [expected] and [actual] are equal after each item transformed to string.
 *
 * If necessary, the failure message will be retrieved lazily from the supplied [message] function.
 */
fun <T> assertToStringEquals(expected: Iterable<T>, actual: Iterable<T>, message: () -> String? = { null }) =
        assertToStringEquals<T, Nothing>(expected, actual, message, null)

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