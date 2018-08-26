package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.reflections.Filter.Exclude
import org.reflections.Filter.Include
import org.reflections.TestModel.AC1
import org.reflections.TestModel.AC1n
import org.reflections.TestModel.AC2
import org.reflections.TestModel.AC3
import org.reflections.TestModel.AF1
import org.reflections.TestModel.AI1
import org.reflections.TestModel.AI2
import org.reflections.TestModel.AM1
import org.reflections.TestModel.C1
import org.reflections.TestModel.C2
import org.reflections.TestModel.C3
import org.reflections.TestModel.C4
import org.reflections.TestModel.C5
import org.reflections.TestModel.C6
import org.reflections.TestModel.C7
import org.reflections.TestModel.I1
import org.reflections.TestModel.I2
import org.reflections.TestModel.I3
import org.reflections.TestModel.MAI1
import org.reflections.TestModel.Usage
import org.reflections.scanners.FieldAnnotationsScanner
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.ResourcesScanner
import org.reflections.scanners.Scanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.scanners.getOrThrow
import org.reflections.util.IndexKey
import org.reflections.util.annotationType
import org.reflections.util.classAndInterfaceHieararchyExceptObject
import org.reflections.util.urlForClass
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.regex.Pattern

open class ReflectionsTest {
    @Test
    fun testSubTypesOf() {
        assertEquals(setOf(I2::class.java, C1::class.java, C2::class.java, C3::class.java, C5::class.java),
                     reflections.getSubTypesOf(I1::class.java))
        assertEquals(setOf(C2::class.java, C3::class.java, C5::class.java), reflections.getSubTypesOf(C1::class.java))
        assertFalse(reflections.allTypes.isEmpty(),
                    "getAllTypes should not be empty when Reflections is configured with SubTypesScanner(false)")
    }

    @Test
    fun testTypesAnnotatedWith() {
        assertEquals(setOf(AI1::class.java), reflections.getTypesAnnotatedWith(MAI1::class.java, true))
        assertAnnotatedWith(MAI1::class.java, reflections.getTypesAnnotatedWith(MAI1::class.java, true))

        assertEquals(setOf(I2::class.java), reflections.getTypesAnnotatedWith(AI2::class.java, true))
        assertAnnotatedWith(AI2::class.java, reflections.getTypesAnnotatedWith(AI2::class.java, true))

        assertEquals(setOf(C1::class.java, C2::class.java, C3::class.java, C5::class.java),
                     reflections.getTypesAnnotatedWith(AC1::class.java, true))
        assertAnnotatedWith(AC1::class.java, reflections.getTypesAnnotatedWith(AC1::class.java, true))

        assertEquals(setOf(C1::class.java), reflections.getTypesAnnotatedWith(AC1n::class.java, true))
        assertAnnotatedWith(AC1n::class.java, reflections.getTypesAnnotatedWith(AC1n::class.java, true))

        assertEquals(setOf(AI1::class.java,
                           I1::class.java,
                           I2::class.java,
                           C1::class.java,
                           C2::class.java,
                           C3::class.java,
                           C5::class.java), reflections.getTypesAnnotatedWith(MAI1::class.java))
        assertMetaAnnotatedWith(MAI1::class.java, reflections.getTypesAnnotatedWith(MAI1::class.java))

        assertEquals(setOf(I1::class.java,
                           I2::class.java,
                           C1::class.java,
                           C2::class.java,
                           C3::class.java,
                           C5::class.java), reflections.getTypesAnnotatedWith(AI1::class.java))
        assertMetaAnnotatedWith(AI1::class.java, reflections.getTypesAnnotatedWith(AI1::class.java))

        assertEquals(setOf(I2::class.java, C1::class.java, C2::class.java, C3::class.java, C5::class.java),
                     reflections.getTypesAnnotatedWith(AI2::class.java))
        assertMetaAnnotatedWith(AI2::class.java, reflections.getTypesAnnotatedWith(AI2::class.java))

        assertEquals(emptySet<Class<*>>(), reflections.getTypesAnnotatedWith(AM1::class.java))

        //annotation member value matching
        val ac2 = JavaSpecific.newAC2("ugh?!")

        assertEquals(setOf(C3::class.java,
                           C5::class.java,
                           I3::class.java,
                           C6::class.java,
                           AC3::class.java,
                           C7::class.java), reflections.getTypesAnnotatedWith(ac2))

        assertEquals(setOf(C3::class.java, I3::class.java, AC3::class.java),
                     reflections.getTypesAnnotatedWith(ac2, true))
    }

    @Test
    fun testMethodsAnnotatedWith() {
        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("m1"),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Int::class.javaPrimitiveType,
                                                                    Array<String>::class.java),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Array<IntArray>::class.java,
                                                                    Array<Array<String>>::class.java),
                                   C4::class.java.getDeclaredMethod("m3")),
                     reflections.getMethodsAnnotatedWith(AM1::class.java))

        val am1 = JavaSpecific.newAM1("1")
        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("m1"),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Int::class.javaPrimitiveType,
                                                                    Array<String>::class.java),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Array<IntArray>::class.java,
                                                                    Array<Array<String>>::class.java)),
                     reflections.getMethodsAnnotatedWith(am1))
    }

    @Test
    fun testConstructorsAnnotatedWith() {
        assertEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                     reflections.getConstructorsAnnotatedWith(AM1::class.java))

        val am1 = JavaSpecific.newAM1("1")
        assertEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                     reflections.getConstructorsAnnotatedWith(am1))
    }

    @Test
    fun testFieldsAnnotatedWith() {
        assertEquals(setOf<Field>(C4::class.java.getDeclaredField("f1"), C4::class.java.getDeclaredField("f2")),
                     reflections.getFieldsAnnotatedWith(AF1::class.java))

        assertEquals(setOf<Field>(C4::class.java.getDeclaredField("f2")),
                     reflections.getFieldsAnnotatedWith(JavaSpecific.newAF1("2")))
    }

    @Test
    fun testMethodParameter() {
        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("m4", String::class.java),
                                   Usage.C1::class.java.getDeclaredMethod("method", String::class.java)),
                     reflections.getMethodsMatchParams(String::class.java))

        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("m1"),
                                   C4::class.java.getDeclaredMethod("m3"),
                                   AC2::class.java.getMethod("value"),
                                   AF1::class.java.getMethod("value"),
                                   AM1::class.java.getMethod("value"),
                                   Usage.C1::class.java.getDeclaredMethod("method"),
                                   Usage.C2::class.java.getDeclaredMethod("method")),
                     reflections.getMethodsMatchParams())

        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("m1",
                                                                    Array<IntArray>::class.java,
                                                                    Array<Array<String>>::class.java)),
                     reflections.getMethodsMatchParams(Array<IntArray>::class.java, Array<Array<String>>::class.java))

        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("add",
                                                                    Int::class.javaPrimitiveType,
                                                                    Int::class.javaPrimitiveType)),
                     reflections.getMethodsReturn(Int::class.javaPrimitiveType!!))

        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("m3"),
                                   C4::class.java.getDeclaredMethod("m4", String::class.java),
                                   AC2::class.java.getMethod("value"),
                                   AF1::class.java.getMethod("value"),
                                   AM1::class.java.getMethod("value")),
                     reflections.getMethodsReturn(String::class.java))

        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("m1"),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Int::class.javaPrimitiveType,
                                                                    Array<String>::class.java),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Array<IntArray>::class.java,
                                                                    Array<Array<String>>::class.java),
                                   Usage.C1::class.java.getDeclaredMethod("method"),
                                   Usage.C1::class.java.getDeclaredMethod("method", String::class.java),
                                   Usage.C2::class.java.getDeclaredMethod("method")),
                     reflections.getMethodsReturn(Void.TYPE))

        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("m4", String::class.java)),
                     reflections.getMethodsWithAnyParamAnnotated(AM1::class.java))

        assertEquals(setOf<Method>(C4::class.java.getDeclaredMethod("m4", String::class.java)),
                     reflections.getMethodsWithAnyParamAnnotated(JavaSpecific.newAM1("2")))
    }

    @Test
    fun testConstructorParameter() {
        assertEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                     reflections.getConstructorsMatchParams(String::class.java))

        assertEquals(setOf<Constructor<out Any>>(C1::class.java.getDeclaredConstructor(),
                                                 C2::class.java.getDeclaredConstructor(),
                                                 C3::class.java.getDeclaredConstructor(),
                                                 C4::class.java.getDeclaredConstructor(),
                                                 C5::class.java.getDeclaredConstructor(),
                                                 C6::class.java.getDeclaredConstructor(),
                                                 C7::class.java.getDeclaredConstructor(),
                                                 Usage.C1::class.java.getDeclaredConstructor(),
                                                 Usage.C2::class.java.getDeclaredConstructor()),
                     reflections.getConstructorsMatchParams())

        assertEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                     reflections.getConstructorsWithAnyParamAnnotated(AM1::class.java))

        assertEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                     reflections.getConstructorsWithAnyParamAnnotated(JavaSpecific.newAM1("1")))
    }

    @Test
    open fun testResourcesScanner() {
        val filter = Filter.Composite(listOf(Include(".*\\.xml"), Exclude(".*testModel-reflections\\.xml")))
        val configuration = Configuration()
        configuration.filter = filter
        configuration.scanners = arrayOf<Scanner>(ResourcesScanner()).toSet()
        configuration.urls = listOfNotNull(urlForClass(TestModel::class.java)).toMutableSet()
        val reflections = Reflections(configuration)

        val resolved = reflections.getResources(Pattern.compile(".*resource1-reflections\\.xml"))
        assertEquals(setOf(IndexKey("META-INF/reflections/resource1-reflections.xml")), resolved)

        val resources = (reflections.stores).getOrThrow<ResourcesScanner>().flatMap { it.store.keys() }.toSet()
        assertEquals(setOf(IndexKey("resource1-reflections.xml"), IndexKey("resource2-reflections.xml")), resources)
    }

    @Test
    fun testMethodParameterNames() {
        assertEquals(emptyList<Any>(), reflections.getParamNames(C4::class.java.getDeclaredMethod("m3")))

        assertEquals(listOf("string"),
                     reflections.getParamNames(C4::class.java.getDeclaredMethod("m4", String::class.java)))

        assertEquals(listOf("i1", "i2"),
                     reflections.getParamNames(C4::class.java.getDeclaredMethod("add",
                                                                                Int::class.javaPrimitiveType,
                                                                                Int::class.javaPrimitiveType)))

        assertEquals(listOf("f1"), reflections.getParamNames(C4::class.java.getDeclaredConstructor(String::class.java)))
    }

    @Test
    fun testMemberUsageScanner() {
        //field usage
        assertEquals(setOf(Usage.C1::class.java.getDeclaredConstructor(),
                           Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java),
                           Usage.C1::class.java.getDeclaredMethod("method"),
                           Usage.C1::class.java.getDeclaredMethod("method", String::class.java)),
                     reflections.getUsage(Usage.C1::class.java.getDeclaredField("c2")))

        //method usage
        assertEquals(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                     reflections.getUsage(Usage.C1::class.java.getDeclaredMethod("method")))

        assertEquals(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                     reflections.getUsage(Usage.C1::class.java.getDeclaredMethod("method", String::class.java)))

        //constructor usage
        assertEquals(setOf(Usage.C2::class.java.getDeclaredConstructor(),
                           Usage.C2::class.java.getDeclaredMethod("method")),
                     reflections.getUsage(Usage.C1::class.java.getDeclaredConstructor()))

        assertEquals(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                     reflections.getUsage(Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java)))
    }

    @Test
    fun testScannerNotConfigured() {
        try {
            Reflections(Configuration(filter = Filter.Composite(listOf(TestModelFilter,
                                                                       Filter.Include(TestModel::class.java.toPackageNameRegex()))),
                                      urls = listOfNotNull(urlForClass(TestModel::class.java)).toSet())).getMethodsAnnotatedWith(
                    AC1::class.java)
            fail<Any>()
        } catch (e: RuntimeException) {
            assertEquals("Scanner ${MethodAnnotationsScanner::class.java.simpleName} was not configured", e.message)
        }
    }

    private fun assertAnnotatedWith(annotation: Class<out Annotation>, classes: Iterable<Class<*>>) =
            assertTrue(classes.all { annotationTypes(it.annotations.asList()).contains(annotation) })

    private fun assertMetaAnnotatedWith(annotation: Class<out Annotation>, classes: Iterable<Class<*>>) =
            assertTrue(classes.all {
                val result = mutableSetOf<Class<*>>()
                val stack = it.classAndInterfaceHieararchyExceptObject().toMutableList()
                while (!stack.isEmpty()) {
                    val next = stack.removeAt(0)
                    if (result.add(next)) {
                        stack += annotationTypes(next.declaredAnnotations.asList()).filter {
                            !result.contains(it) && !stack.contains(it)
                        }
                    }
                }
                result.contains(annotation)
            })

    private fun annotationTypes(annotations: Iterable<Annotation>) = annotations.map { input -> input.annotationType() }

    companion object {
        val TestModelFilter = Include("org.reflections.TestModel\\$.*")

        @JvmStatic lateinit var reflections: Reflections

        @BeforeAll
        @JvmStatic
        fun init() {
            val configuration = Configuration()
            configuration.urls = listOfNotNull(urlForClass(TestModel::class.java)).toMutableSet()
            configuration.filter = TestModelFilter
            configuration.scanners =
                    setOf(SubTypesScanner(false),
                          TypeAnnotationsScanner(),
                          FieldAnnotationsScanner(),
                          MethodAnnotationsScanner(),
                          MethodParameterScanner(),
                          MethodParameterNamesScanner(),
                          MemberUsageScanner())
            reflections = Reflections(configuration)
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
    }
}
