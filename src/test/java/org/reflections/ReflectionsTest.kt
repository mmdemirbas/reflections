package org.reflections

import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.reflections.TestModel.*
import org.reflections.scanners.*
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.reflections.util.Utils.index
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.*
import java.util.regex.Pattern

/**
 *
 */
open class ReflectionsTest {

    private val isEmpty = object : BaseMatcher<Set<Class<*>>>() {
        override fun matches(o: Any): Boolean {
            return (o as Collection<*>).isEmpty()
        }

        override fun describeTo(description: Description) {
            description.appendText("empty collection")
        }
    }

    @Test
    fun testSubTypesOf() {
        assertEquals(reflections.getSubTypesOf(I1::class.java),
                     setOf(I2::class.java, C1::class.java, C2::class.java, C3::class.java, C5::class.java))
        assertThat(reflections.getSubTypesOf(C1::class.java), are(C2::class.java, C3::class.java, C5::class.java))

        assertFalse("getAllTypes should not be empty when Reflections is configured with SubTypesScanner(false)",
                    reflections.allTypes.isEmpty())
    }

    @Test
    fun testTypesAnnotatedWith() {
        assertEquals(reflections.getTypesAnnotatedWith(MAI1::class.java, true), setOf(AI1::class.java))
        assertThat(reflections.getTypesAnnotatedWith(MAI1::class.java, true), annotatedWith(MAI1::class.java))

        assertEquals(reflections.getTypesAnnotatedWith(AI2::class.java, true), setOf(I2::class.java))
        assertThat(reflections.getTypesAnnotatedWith(AI2::class.java, true), annotatedWith(AI2::class.java))

        assertEquals(reflections.getTypesAnnotatedWith(AC1::class.java, true),
                     setOf(C1::class.java, C2::class.java, C3::class.java, C5::class.java))
        assertThat(reflections.getTypesAnnotatedWith(AC1::class.java, true), annotatedWith(AC1::class.java))

        assertEquals(reflections.getTypesAnnotatedWith(AC1n::class.java, true), setOf(C1::class.java))
        assertThat(reflections.getTypesAnnotatedWith(AC1n::class.java, true), annotatedWith(AC1n::class.java))

        assertThat(reflections.getTypesAnnotatedWith(MAI1::class.java),
                   are(AI1::class.java,
                       I1::class.java,
                       I2::class.java,
                       C1::class.java,
                       C2::class.java,
                       C3::class.java,
                       C5::class.java))
        assertThat(reflections.getTypesAnnotatedWith(MAI1::class.java), metaAnnotatedWith(MAI1::class.java))

        assertEquals(reflections.getTypesAnnotatedWith(AI1::class.java),
                     setOf(I1::class.java,
                           I2::class.java,
                           C1::class.java,
                           C2::class.java,
                           C3::class.java,
                           C5::class.java))
        assertThat(reflections.getTypesAnnotatedWith(AI1::class.java), metaAnnotatedWith(AI1::class.java))

        assertEquals(reflections.getTypesAnnotatedWith(AI2::class.java),
                     setOf(I2::class.java, C1::class.java, C2::class.java, C3::class.java, C5::class.java))
        assertThat(reflections.getTypesAnnotatedWith(AI2::class.java), metaAnnotatedWith(AI2::class.java))

        assertThat(reflections.getTypesAnnotatedWith(AM1::class.java), isEmpty)

        //annotation member value matching
        val ac2 = JavaSpecific.newAC2("ugh?!")

        assertThat(reflections.getTypesAnnotatedWith(ac2),
                   are(C3::class.java, C5::class.java, I3::class.java, C6::class.java, AC3::class.java, C7::class.java))

        assertThat(reflections.getTypesAnnotatedWith(ac2, true), are(C3::class.java, I3::class.java, AC3::class.java))
    }

    @Test
    fun testMethodsAnnotatedWith() {
        try {
            assertThat<Set<Method>>(reflections.getMethodsAnnotatedWith(AM1::class.java),
                                    are<Method>(C4::class.java.getDeclaredMethod("m1"),
                                                C4::class.java.getDeclaredMethod("m1",
                                                                                 Int::class.javaPrimitiveType,
                                                                                 Array<String>::class.java),
                                                C4::class.java.getDeclaredMethod("m1",
                                                                                 Array<IntArray>::class.java,
                                                                                 Array<Array<String>>::class.java),
                                                C4::class.java.getDeclaredMethod("m3")))

            val am1 = JavaSpecific.newAM1("1")
            assertThat<Set<Method>>(reflections.getMethodsAnnotatedWith(am1),
                                    are<Method>(C4::class.java.getDeclaredMethod("m1"),
                                                C4::class.java.getDeclaredMethod("m1",
                                                                                 Int::class.javaPrimitiveType,
                                                                                 Array<String>::class.java),
                                                C4::class.java.getDeclaredMethod("m1",
                                                                                 Array<IntArray>::class.java,
                                                                                 Array<Array<String>>::class.java)))
        } catch (e: NoSuchMethodException) {
            fail()
        }

    }

    @Test
    fun testConstructorsAnnotatedWith() {
        try {
            assertThat<Set<Constructor<*>>>(reflections.getConstructorsAnnotatedWith(AM1::class.java),
                                            are(C4::class.java.getDeclaredConstructor(String::class.java)))

            val am1 = JavaSpecific.newAM1("1")
            assertThat<Set<Constructor<*>>>(reflections.getConstructorsAnnotatedWith(am1),
                                            are(C4::class.java.getDeclaredConstructor(String::class.java)))
        } catch (e: NoSuchMethodException) {
            fail()
        }

    }

    @Test
    fun testFieldsAnnotatedWith() {
        try {
            assertThat<Set<Field>>(reflections.getFieldsAnnotatedWith(AF1::class.java),
                                   are<Field>(C4::class.java.getDeclaredField("f1"),
                                              C4::class.java.getDeclaredField("f2")))

            assertThat<Set<Field>>(reflections.getFieldsAnnotatedWith(JavaSpecific.newAF1("2")),
                                   are<Field>(C4::class.java.getDeclaredField("f2")))
        } catch (e: NoSuchFieldException) {
            fail()
        }

    }

    @Test
    fun testMethodParameter() {
        try {
            assertThat<Set<Method>>(reflections.getMethodsMatchParams(String::class.java),
                                    are<Method>(C4::class.java.getDeclaredMethod("m4", String::class.java),
                                                Usage.C1::class.java.getDeclaredMethod("method", String::class.java)))

            assertThat<Set<Method>>(reflections.getMethodsMatchParams(),
                                    are<Method>(C4::class.java.getDeclaredMethod("m1"),
                                                C4::class.java.getDeclaredMethod("m3"),
                                                AC2::class.java.getMethod("value"),
                                                AF1::class.java.getMethod("value"),
                                                AM1::class.java.getMethod("value"),
                                                Usage.C1::class.java.getDeclaredMethod("method"),
                                                Usage.C2::class.java.getDeclaredMethod("method")))

            assertThat<Set<Method>>(reflections.getMethodsMatchParams(Array<IntArray>::class.java,
                                                                      Array<Array<String>>::class.java),
                                    are<Method>(C4::class.java.getDeclaredMethod("m1",
                                                                                 Array<IntArray>::class.java,
                                                                                 Array<Array<String>>::class.java)))

            assertThat<Set<Method>>(reflections.getMethodsReturn(Int::class.javaPrimitiveType!!),
                                    are<Method>(C4::class.java.getDeclaredMethod("add",
                                                                                 Int::class.javaPrimitiveType,
                                                                                 Int::class.javaPrimitiveType)))

            assertThat<Set<Method>>(reflections.getMethodsReturn(String::class.java),
                                    are<Method>(C4::class.java.getDeclaredMethod("m3"),
                                                C4::class.java.getDeclaredMethod("m4", String::class.java),
                                                AC2::class.java.getMethod("value"),
                                                AF1::class.java.getMethod("value"),
                                                AM1::class.java.getMethod("value")))

            assertThat<Set<Method>>(reflections.getMethodsReturn(Void.TYPE),
                                    are<Method>(C4::class.java.getDeclaredMethod("m1"),
                                                C4::class.java.getDeclaredMethod("m1",
                                                                                 Int::class.javaPrimitiveType,
                                                                                 Array<String>::class.java),
                                                C4::class.java.getDeclaredMethod("m1",
                                                                                 Array<IntArray>::class.java,
                                                                                 Array<Array<String>>::class.java),
                                                Usage.C1::class.java.getDeclaredMethod("method"),
                                                Usage.C1::class.java.getDeclaredMethod("method", String::class.java),
                                                Usage.C2::class.java.getDeclaredMethod("method")))

            assertThat<Set<Method>>(reflections.getMethodsWithAnyParamAnnotated(AM1::class.java),
                                    are<Method>(C4::class.java.getDeclaredMethod("m4", String::class.java)))

            assertThat<Set<Method>>(reflections.getMethodsWithAnyParamAnnotated(JavaSpecific.newAM1("2")),
                                    are<Method>(C4::class.java.getDeclaredMethod("m4", String::class.java)))
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        }

    }

    @Test
    @Throws(NoSuchMethodException::class)
    fun testConstructorParameter() {
        assertThat<Set<Constructor<*>>>(reflections.getConstructorsMatchParams(String::class.java),
                                        are(C4::class.java.getDeclaredConstructor(String::class.java)))

        assertThat<Set<Constructor<*>>>(reflections.getConstructorsMatchParams(),
                                        are<Constructor<out Any>>(C1::class.java.getDeclaredConstructor(),
                                                                  C2::class.java.getDeclaredConstructor(),
                                                                  C3::class.java.getDeclaredConstructor(),
                                                                  C4::class.java.getDeclaredConstructor(),
                                                                  C5::class.java.getDeclaredConstructor(),
                                                                  C6::class.java.getDeclaredConstructor(),
                                                                  C7::class.java.getDeclaredConstructor(),
                                                                  Usage.C1::class.java.getDeclaredConstructor(),
                                                                  Usage.C2::class.java.getDeclaredConstructor()))

        assertThat<Set<Constructor<*>>>(reflections.getConstructorsWithAnyParamAnnotated(AM1::class.java),
                                        are(C4::class.java.getDeclaredConstructor(String::class.java)))

        assertThat<Set<Constructor<*>>>(reflections.getConstructorsWithAnyParamAnnotated(JavaSpecific.newAM1("1")),
                                        are(C4::class.java.getDeclaredConstructor(String::class.java)))
    }

    @Test
    open fun testResourcesScanner() {
        val filter = FilterBuilder().include(".*\\.xml").exclude(".*testModel-reflections\\.xml").asPredicate()
        val reflections =
                Reflections(ConfigurationBuilder().filterInputsBy(filter).setScanners(ResourcesScanner()).setUrls(
                        listOfNotNull(ClasspathHelper.forClass(TestModel::class.java))))

        val resolved = reflections.getResources(Pattern.compile(".*resource1-reflections\\.xml"))
        assertThat(resolved, are("META-INF/reflections/resource1-reflections.xml"))

        val resources = reflections.store!![index(ResourcesScanner::class.java)].keySet()
        assertThat(resources, are("resource1-reflections.xml", "resource2-reflections.xml"))
    }

    @Test
    @Throws(NoSuchMethodException::class)
    fun testMethodParameterNames() {
        assertEquals(reflections.getMethodParamNames(C4::class.java.getDeclaredMethod("m3")), listOf<Any>())

        assertEquals(reflections.getMethodParamNames(C4::class.java.getDeclaredMethod("m4", String::class.java)),
                     listOf("string"))

        assertEquals(reflections.getMethodParamNames(C4::class.java.getDeclaredMethod("add",
                                                                                      Int::class.javaPrimitiveType,
                                                                                      Int::class.javaPrimitiveType)),
                     listOf("i1", "i2"))

        assertEquals(reflections.getConstructorParamNames(C4::class.java.getDeclaredConstructor(String::class.java)),
                     listOf("f1"))
    }

    @Test
    @Throws(NoSuchFieldException::class, NoSuchMethodException::class)
    fun testMemberUsageScanner() {
        //field usage
        assertThat<Set<Member>>(reflections.getFieldUsage(Usage.C1::class.java.getDeclaredField("c2")),
                                are(Usage.C1::class.java.getDeclaredConstructor(),
                                    Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java),
                                    Usage.C1::class.java.getDeclaredMethod("method"),
                                    Usage.C1::class.java.getDeclaredMethod("method", String::class.java)))

        //method usage
        assertThat<Set<Member>>(reflections.getMethodUsage(Usage.C1::class.java.getDeclaredMethod("method")),
                                are(Usage.C2::class.java.getDeclaredMethod("method")))

        assertThat<Set<Member>>(reflections.getMethodUsage(Usage.C1::class.java.getDeclaredMethod("method",
                                                                                                  String::class.java)),
                                are(Usage.C2::class.java.getDeclaredMethod("method")))

        //constructor usage
        assertThat<Set<Member>>(reflections.getConstructorUsage(Usage.C1::class.java.getDeclaredConstructor()),
                                are(Usage.C2::class.java.getDeclaredConstructor(),
                                    Usage.C2::class.java.getDeclaredMethod("method")))

        assertThat<Set<Member>>(reflections.getConstructorUsage(Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java)),
                                are(Usage.C2::class.java.getDeclaredMethod("method")))
    }

    @Test
    fun testScannerNotConfigured() {
        try {
            Reflections(TestModel::class.java, TestModelFilter).getMethodsAnnotatedWith(AC1::class.java)
            fail()
        } catch (e: ReflectionsException) {
            assertEquals(e.message,
                         "Scanner " + MethodAnnotationsScanner::class.java.simpleName + " was not configured")
        }

    }

    private abstract class Match<T> : BaseMatcher<T>() {

        override fun describeTo(description: Description) {}
    }

    private fun annotatedWith(annotation: Class<out Annotation>): Matcher<Set<Class<*>>> {
        return object : Match<Set<Class<*>>>() {
            override fun matches(o: Any): Boolean {
                for (c in o as Iterable<Class<*>>) {
                    if (!annotationTypes(Arrays.asList(*c.annotations)).contains(annotation)) {
                        return false
                    }
                }
                return true
            }
        }
    }

    private fun metaAnnotatedWith(annotation: Class<out Annotation>): Matcher<Set<Class<*>>> {
        return object : Match<Set<Class<*>>>() {
            override fun matches(o: Any): Boolean {
                for (c in o as Iterable<Class<*>>) {
                    val result = mutableSetOf<Class<*>>()
                    val stack = ReflectionUtils.getAllSuperTypes(c).toMutableList()
                    while (!stack.isEmpty()) {
                        val next = stack.removeAt(0)
                        if (result.add(next)) {
                            for (ac in annotationTypes(Arrays.asList(*next.declaredAnnotations))) {
                                if (!result.contains(ac) && !stack.contains(ac)) {
                                    stack.add(ac)
                                }
                            }
                        }
                    }
                    if (!result.contains(annotation)) {
                        return false
                    }
                }
                return true
            }
        }
    }

    private fun annotationTypes(annotations: Iterable<Annotation>): Iterable<Class<out Annotation>> {
        return annotations.map { input -> input.annotationType() }
    }

    companion object {

        val TestModelFilter = FilterBuilder().include("org.reflections.TestModel\\$.*")

        @JvmStatic lateinit var reflections: Reflections

        @BeforeClass
        @JvmStatic
        fun init() {
            reflections =
                    Reflections(ConfigurationBuilder().setUrls(listOfNotNull(ClasspathHelper.forClass(TestModel::class.java))).filterInputsBy(
                            TestModelFilter.asPredicate()).setScanners(SubTypesScanner(false),
                                                                       TypeAnnotationsScanner(),
                                                                       FieldAnnotationsScanner(),
                                                                       MethodAnnotationsScanner(),
                                                                       MethodParameterScanner(),
                                                                       MethodParameterNamesScanner(),
                                                                       MemberUsageScanner()))
        }

        //
        //a hack to fix user.dir issue(?) in surfire
        val userDir: String
            get() {
                var file = File(System.getProperty("user.dir"))
                if (listOf(*file.list()!!).contains("reflections")) {
                    file = File(file, "reflections")
                }
                return file.absolutePath
            }

        fun <T> are(vararg ts: T): Matcher<Set<T>> {
            val c1 = Arrays.asList(*ts)
            return object : Match<Set<T>>() {
                override fun matches(o: Any): Boolean {
                    val c2 = o as Collection<*>
                    return c1.containsAll(c2) && c2.containsAll(c1)
                }
            }
        }
    }
}
