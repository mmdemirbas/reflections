package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.reflections.ReflectionUtils.getAllMethods
import org.reflections.ReflectionUtils.withModifier
import org.reflections.ReflectionUtils.withParameters
import org.reflections.ReflectionUtils.withParametersAssignableFrom
import org.reflections.ReflectionUtils.withParametersAssignableTo
import org.reflections.TestModel.AF1
import org.reflections.TestModel.AI1
import org.reflections.TestModel.AM1
import org.reflections.TestModel.C3
import org.reflections.TestModel.C4
import org.reflections.TestModel.I1
import org.reflections.scanners.FieldAnnotationsScanner
import org.reflections.util.classAndInterfaceHieararchyExceptObject
import org.reflections.util.urlForClass
import org.reflections.util.withAnnotation
import org.reflections.util.withAnyParameterAnnotation
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.regex.Pattern

/**
 * @author mamo
 */
class ReflectionUtilsTest {
    @Test
    fun getAllTest() {
        assertEquals(listOf(I1::class.java),
                     C3::class.java.classAndInterfaceHieararchyExceptObject().filter { it.isAnnotationPresent(AI1::class.java) })

        val allMethods =
                getAllMethods(C4::class.java).filter(andPredicate({ withModifier(it, Modifier.PUBLIC) },
                                                                  { it.returnType == Void.TYPE })).toSet()
        val allMethods1 = getAllMethods(C4::class.java).filter { it: Method ->
            Pattern.matches("public.*.void .*", it.toString())
        }.toSet()

        assertTrue(allMethods.containsAll(allMethods1) && allMethods1.containsAll(allMethods))
        assertHasNames(listOf("m1", "m1"), allMethods1)

        assertHasNames(listOf("m4"), getAllMethods(C4::class.java).filter { it: Method ->
            withAnyParameterAnnotation(it, AM1::class.java)
        }.toSet())

        assertHasNames(listOf("f1", "f2"),
                       ReflectionUtils.getAllFields(C4::class.java) { it.isAnnotationPresent(AF1::class.java) })

        assertHasNames(listOf("f2"), ReflectionUtils.getAllFields(C4::class.java) {
            withAnnotation(it, JavaSpecific.newAF1("2"))
        })

        assertHasNames(listOf("f1", "f2", "f3"), ReflectionUtils.getAllFields(C4::class.java) {
            String::class.java.isAssignableFrom(it.type)
        })

        assertEquals(ReflectionUtils.getAllAnnotations(C3::class.java).size.toLong(), 5)

        val m4 = C4::class.java.declaredMethods.filter { it.name == "m4" }.iterator().next()
        assertEquals("m4", m4.name)
        assertTrue(ReflectionUtils.getAnnotations(m4).isEmpty())
    }

    @Test
    fun withParameter() {
        val target = Collections::class.java
        val arg1 = listOf(1, 2, 3)

        val allMethods = mutableSetOf<Method>()
        for (type in arg1.javaClass.classAndInterfaceHieararchyExceptObject()) {
            allMethods.addAll(getAllMethods(target).filter(andPredicate({ withModifier(it, Modifier.STATIC) },
                                                                        { withParameters(it, listOf(type)) })).toSet())
        }

        val allMethods1 =
                getAllMethods(target).filter(andPredicate({ withModifier(it, Modifier.STATIC) },
                                                          { withParametersAssignableTo(it, listOf(arg1.javaClass)) }))
                    .toSet()

        assertEquals(allMethods1, allMethods)

        for (method in allMethods) { //effectively invokable
            val invoke = method.invoke(null, arg1)
        }
    }

    @Test
    fun withParametersAssignableFromTest() {
        //Check for null safe
        getAllMethods(Collections::class.java).filter(andPredicate({ withModifier(it, Modifier.STATIC) },
                                                                   { withParametersAssignableFrom(it, emptyList()) }))
            .toSet()

        val target = Collections::class.java
        val arg1 = listOf(1, 2, 3)

        val allMethods = mutableSetOf<Method>()
        for (type in arg1.javaClass.classAndInterfaceHieararchyExceptObject()) {
            allMethods.addAll(getAllMethods(target).filter(andPredicate({ withModifier(it, Modifier.STATIC) },
                                                                        { withParameters(it, listOf(type)) })).toSet())
        }

        val allMethods1 = getAllMethods(target).filter(andPredicate({ withModifier(it, Modifier.STATIC) }, {
            withParametersAssignableFrom(it, listOf(Iterable::class.java))
        }, { withParametersAssignableTo(it, listOf(arg1.javaClass)) })).toSet()

        assertEquals(allMethods, allMethods1)

        for (method in allMethods) { //effectively invokable
            val invoke = method.invoke(null, arg1)
        }
    }

    @Test
    fun withReturn() {
        val returnMember =
                getAllMethods(Class::class.java).filter { it: Method -> Member::class.java.isAssignableFrom(it.returnType) }
                    .toSet()
        var returnsAssignableToMember =
                getAllMethods(Class::class.java).filter { it: Method -> it.returnType == Method::class.java }.toSet()

        assertTrue(returnMember.containsAll(returnsAssignableToMember))
        assertFalse(returnsAssignableToMember.containsAll(returnMember))

        returnsAssignableToMember =
                getAllMethods(Class::class.java).filter { it: Method -> it.returnType == Field::class.java }.toSet()
        assertTrue(returnMember.containsAll(returnsAssignableToMember))
        assertFalse(returnsAssignableToMember.containsAll(returnMember))
    }

    @Test
    fun getAllAndReflections() {
        val reflections =
                Reflections(Configuration(filter = Filter.Include(TestModel::class.java.toPackageNameRegex()),
                                          urls = listOfNotNull(urlForClass(TestModel::class.java)).toSet(),
                                          scanners = setOf(FieldAnnotationsScanner())))

        val allFields =
                reflections.getFieldsAnnotatedWith(AF1::class.java).filter { withModifier(it, Modifier.PROTECTED) }
                    .toSet()
        assertEquals(1, allFields.size.toLong())
        assertHasNames(listOf("f2"), allFields)
    }

    private fun assertHasNames(expected: List<String>, members: Iterable<Member>) =
            assertEquals(expected, members.map { it.name })

    private fun <T> andPredicate(vararg predicates: (T) -> Boolean): (T) -> Boolean =
            { input -> predicates.all { predicate -> predicate(input) } }
}
