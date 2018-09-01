package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.reflections.TestModel.AF1
import org.reflections.TestModel.AI1
import org.reflections.TestModel.AM1
import org.reflections.TestModel.C3
import org.reflections.TestModel.C4
import org.reflections.TestModel.I1
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.regex.Pattern

/**
 * @author mamo
 */
class TestUtilsTests {
    // todo: split these tests

    @Test
    fun getAllTest() {
        assertEquals(listOf(I1::class.java),
                     C3::class.java.neededHierarchy().filter { it.isAnnotationPresent(AI1::class.java) })

        val allMethods =
                C4::class.java.allMethods().filter { it.hasModifier(Modifier.PUBLIC) && it.returnType == Void.TYPE }
        val allMethods1 = C4::class.java.allMethods().filter { it: Method ->
            Pattern.matches("public.*.void .*", it.toString())
        }

        assertTrue(allMethods.containsAll(allMethods1) && allMethods1.containsAll(allMethods))
        assertHasNames(listOf("m1", "m1"), allMethods1)

        assertHasNames(listOf("m4"), C4::class.java.allMethods().filter { it: Method ->
            withAnyParameterAnnotation(it, AM1::class.java)
        })

        assertHasNames(listOf("f1", "f2", "f3"), C4::class.java.allFields())

        assertHasNames(listOf("f2"), C4::class.java.allFields().filter {
            withAnnotation(it, JavaSpecific.newAF1("2"))
        })

        assertEquals(C3::class.java.allAnnotations().size.toLong(), 5)

        val m4 = C4::class.java.declaredMethods.filter { it.name == "m4" }.iterator().next()
        assertEquals("m4", m4.name)
    }

    @Test
    fun withParameter() {
        val target = Collections::class.java
        val listOfInt = listOf(1, 2, 3)

        val allMethods = listOfInt.javaClass.neededHierarchy().flatMap { type ->
            target.allMethods().filter { method ->
                method.hasModifier(Modifier.STATIC) && method.hasParamTypes(listOf(type))
            }
        }

        val allMethods1 = target.allMethods().filter { method ->
            method.hasModifier(Modifier.STATIC) && method.hasParamTypesSubtypesOf(listOf(listOfInt.javaClass))
        }

        assertEquals(allMethods1, allMethods)

        allMethods.forEach { method ->
            //effectively invokable
            val invoke = method.invoke(null, listOfInt)
        }
    }

    @Test
    fun withParametersAssignableFromTest() {
        //Check for null safe
        Collections::class.java.allMethods().filter {
            it.hasModifier(Modifier.STATIC) && it.hasParamTypesSupertypesOf(emptyList())
        }

        val target = Collections::class.java
        val listOfInt = listOf(1, 2, 3)

        val allMethods = listOfInt.javaClass.neededHierarchy().flatMap { type ->
            target.allMethods().filter { method ->
                method.hasModifier(Modifier.STATIC) && method.hasParamTypes(listOf(type))
            }
        }

        val allMethods1 = target.allMethods().filter { method ->
            method.hasModifier(Modifier.STATIC) && method.hasParamTypesSupertypesOf(listOf(Iterable::class.java)) && method.hasParamTypesSubtypesOf(
                    listOf(listOfInt.javaClass))
        }

        assertEquals(allMethods, allMethods1)

        allMethods.forEach { method ->
            //effectively invokable
            val invoke = method.invoke(null, listOfInt)
        }
    }

    @Test
    fun withReturn() {
        val returnMember =
                Class::class.java.allMethods()
                    .filter { it: Method -> Member::class.java.isAssignableFrom(it.returnType) }
        var returnsAssignableToMember =
                Class::class.java.allMethods().filter { it: Method -> it.returnType == Method::class.java }

        assertTrue(returnMember.containsAll(returnsAssignableToMember))
        assertFalse(returnsAssignableToMember.containsAll(returnMember))

        returnsAssignableToMember =
                Class::class.java.allMethods().filter { it: Method -> it.returnType == Field::class.java }
        assertTrue(returnMember.containsAll(returnsAssignableToMember))
        assertFalse(returnsAssignableToMember.containsAll(returnMember))
    }

    @Test
    fun getAllAndReflections() {

        val allFields =
                FieldAnnotationsScanner().scan(TestModel::class.java).fieldsAnnotatedWith(AF1::class.java)
                    .filter { it.hasModifier(Modifier.PROTECTED) }

        assertEquals(1, allFields.size.toLong())
        assertHasNames(listOf("f2"), allFields)
    }

    private fun assertHasNames(expected: Collection<String>, members: Iterable<Member>) =
            assertEquals(expected, members.map { it.name })
}
