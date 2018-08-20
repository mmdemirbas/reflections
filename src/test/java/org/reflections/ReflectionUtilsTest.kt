package org.reflections

import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.reflections.TestModel.AF1
import org.reflections.TestModel.AI1
import org.reflections.TestModel.AM1
import org.reflections.TestModel.C3
import org.reflections.TestModel.C4
import org.reflections.TestModel.I1
import org.reflections.scanners.FieldAnnotationsScanner
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

/**
 * @author mamo
 */
class ReflectionUtilsTest {

    @Test
    fun getAllTest() {
        assertEquals(ReflectionUtils.getAllSuperTypes(C3::class.java,
                                                      false,
                                                      ReflectionUtils.withAnnotation(AI1::class.java)),
                     setOf(I1::class.java))

        val allMethods =
                ReflectionUtils.getAllMethods(C4::class.java,
                                              ReflectionUtils.withModifier(Modifier.PUBLIC),
                                              ReflectionUtils.withReturnType(Void.TYPE))
        val allMethods1 = ReflectionUtils.getAllMethods(C4::class.java, ReflectionUtils.withPattern("public.*.void .*"))

        assertTrue(allMethods.containsAll(allMethods1) && allMethods1.containsAll(allMethods))
        assertThat<Set<Method>>(allMethods1, names("m1"))

        assertThat<Set<Method>>(ReflectionUtils.getAllMethods(C4::class.java,
                                                              ReflectionUtils.withAnyParameterAnnotation(AM1::class.java)),
                                names("m4"))

        assertThat<Set<Field>>(ReflectionUtils.getAllFields(C4::class.java,
                                                            ReflectionUtils.withAnnotation(AF1::class.java)),
                               names("f1", "f2"))

        assertThat<Set<Field>>(ReflectionUtils.getAllFields(C4::class.java,
                                                            ReflectionUtils.withAnnotation(JavaSpecific.newAF1("2"))),
                               names("f2"))

        assertThat<Set<Field>>(ReflectionUtils.getAllFields(C4::class.java,
                                                            ReflectionUtils.withTypeAssignableTo(String::class.java)),
                               names("f1", "f2", "f3"))

        assertThat<Set<Constructor<*>>>(ReflectionUtils.getAllConstructors(C4::class.java,
                                                                           ReflectionUtils.withParametersCount(0)),
                                        names(C4::class.java.name))

        assertEquals(ReflectionUtils.getAllAnnotations(C3::class.java).size.toLong(), 5)

        val m4 = ReflectionUtils.getMethods(C4::class.java, ReflectionUtils.withName("m4")).iterator().next()
        assertEquals(m4.name, "m4")
        assertTrue(ReflectionUtils.getAnnotations(m4).isEmpty())
    }

    @Test
    @Throws(Exception::class)
    fun withParameter() {
        val target = Collections::class.java
        val arg1 = Arrays.asList(1, 2, 3)

        val allMethods = mutableSetOf<Method>()
        for (type in ReflectionUtils.getAllSuperTypes(arg1.javaClass)) {
            allMethods.addAll(ReflectionUtils.getAllMethods(target,
                                                            ReflectionUtils.withModifier(Modifier.STATIC),
                                                            ReflectionUtils.withParameters(type)))
        }

        val allMethods1 =
                ReflectionUtils.getAllMethods(target,
                                              ReflectionUtils.withModifier(Modifier.STATIC),
                                              ReflectionUtils.withParametersAssignableTo(arg1.javaClass))

        assertEquals(allMethods, allMethods1)

        for (method in allMethods) { //effectively invokable
            val invoke = method.invoke(null, arg1)
        }
    }

    @Test
    @Throws(Exception::class)
    fun withParametersAssignableFromTest() {
        //Check for null safe
        ReflectionUtils.getAllMethods(Collections::class.java,
                                      ReflectionUtils.withModifier(Modifier.STATIC),
                                      ReflectionUtils.withParametersAssignableFrom())

        val target = Collections::class.java
        val arg1 = Arrays.asList(1, 2, 3)

        val allMethods = mutableSetOf<Method>()
        for (type in ReflectionUtils.getAllSuperTypes(arg1.javaClass)) {
            allMethods.addAll(ReflectionUtils.getAllMethods(target,
                                                            ReflectionUtils.withModifier(Modifier.STATIC),
                                                            ReflectionUtils.withParameters(type)))
        }

        val allMethods1 =
                ReflectionUtils.getAllMethods(target,
                                              ReflectionUtils.withModifier(Modifier.STATIC),
                                              ReflectionUtils.withParametersAssignableFrom(Iterable::class.java),
                                              ReflectionUtils.withParametersAssignableTo(arg1.javaClass))

        assertEquals(allMethods, allMethods1)

        for (method in allMethods) { //effectively invokable
            val invoke = method.invoke(null, arg1)
        }
    }

    @Test
    fun withReturn() {
        val returnMember =
                ReflectionUtils.getAllMethods(Class::class.java,
                                              ReflectionUtils.withReturnTypeAssignableTo(Member::class.java))
        var returnsAssignableToMember =
                ReflectionUtils.getAllMethods(Class::class.java, ReflectionUtils.withReturnType(Method::class.java))

        assertTrue(returnMember.containsAll(returnsAssignableToMember))
        assertFalse(returnsAssignableToMember.containsAll(returnMember))

        returnsAssignableToMember =
                ReflectionUtils.getAllMethods(Class::class.java, ReflectionUtils.withReturnType(Field::class.java))
        assertTrue(returnMember.containsAll(returnsAssignableToMember))
        assertFalse(returnsAssignableToMember.containsAll(returnMember))
    }

    @Test
    fun getAllAndReflections() {
        val reflections = Reflections(TestModel::class.java, FieldAnnotationsScanner())

        val af1 = reflections.getFieldsAnnotatedWith(AF1::class.java)
        val allFields = ReflectionUtils.getAll(af1, ReflectionUtils.withModifier(Modifier.PROTECTED))
        assertEquals(1, allFields.size.toLong())
        assertThat<Set<Field>>(allFields, names("f2"))
    }

    private fun names(o: Set<Member>): Set<String> {
        return o.map { input: Member -> input.name }.toSet()
    }

    private fun names(vararg namesArray: String): BaseMatcher<Set<Member>> {
        return object : BaseMatcher<Set<Member>>() {

            override fun matches(o: Any): Boolean {
                val transform = names(o as Set<Member>)
                val names = Arrays.asList(*namesArray)
                return transform.containsAll(names) && names.containsAll(transform)
            }

            override fun describeTo(description: Description) {}
        }
    }
}
