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
import org.reflections.util.ReflectionUtils
import org.reflections.util.ReflectionUtils.getAllMethods
import org.reflections.util.ReflectionUtils.withModifier
import org.reflections.util.ReflectionUtils.withParameters
import org.reflections.util.ReflectionUtils.withParametersAssignableFrom
import org.reflections.util.ReflectionUtils.withParametersAssignableTo
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
        assertThat<Set<Method>>(allMethods1, names("m1"))

        assertThat<Set<Method>>(getAllMethods(C4::class.java).filter { it: Method ->
            withAnyParameterAnnotation(it, AM1::class.java)
        }.toSet(), names("m4"))

        assertThat<Set<Field>>(ReflectionUtils.getAllFields(C4::class.java) { it.isAnnotationPresent(AF1::class.java) },
                               names("f1", "f2"))

        assertThat<Set<Field>>(ReflectionUtils.getAllFields(C4::class.java) {
            withAnnotation(it, JavaSpecific.newAF1("2"))
        }, names("f2"))

        assertThat<Set<Field>>(ReflectionUtils.getAllFields(C4::class.java) {
            String::class.java.isAssignableFrom(it.type)
        }, names("f1", "f2", "f3"))

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
        assertThat<Set<Field>>(allFields, names("f2"))
    }

    private fun names(o: Set<Member>): Set<String> {
        return o.map { it.name }.toSet()
    }

    private fun names(vararg namesArray: String): BaseMatcher<Set<Member>> {
        return object : BaseMatcher<Set<Member>>() {
            override fun matches(o: Any): Boolean {
                val transform = names(o as Set<Member>)
                val names = namesArray.asList()
                return transform.containsAll(names) && names.containsAll(transform)
            }

            override fun describeTo(description: Description) {}
        }
    }

    private fun <T> andPredicate(vararg predicates: (T) -> Boolean): (T) -> Boolean =
            { input -> predicates.all { predicate -> predicate(input) } }
}
