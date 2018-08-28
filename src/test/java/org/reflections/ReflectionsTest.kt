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
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.serializers.JsonSerializer
import org.reflections.util.Datum
import org.reflections.util.annotationType
import org.reflections.util.classAndInterfaceHieararchyExceptObject
import org.reflections.util.urlForClass
import java.io.File
import java.util.regex.Pattern

open class ReflectionsTest {
    @Test
    fun testSubTypesOf() {
        assertToStringEquals(setOf(I2::class.java, C1::class.java, C2::class.java, C3::class.java, C5::class.java),
                             reflections.subTypesOf(I1::class.java), sortBy = Any::toString)
        assertToStringEquals(setOf(C2::class.java, C3::class.java, C5::class.java),
                             reflections.subTypesOf(C1::class.java), sortBy = Any::toString)
        assertFalse(reflections.allTypes().isEmpty(),
                    "allTypes should not be empty when Reflections is configured with SubTypesScanner(false)")
    }

    @Test
    fun testTypesAnnotatedWith() {
        assertToStringEquals(setOf(AI1::class.java), reflections.getTypesAnnotatedWith(MAI1::class.java, true))
        assertAnnotatedWith(MAI1::class.java, reflections.getTypesAnnotatedWith(MAI1::class.java, true))

        assertToStringEquals(setOf(I2::class.java), reflections.getTypesAnnotatedWith(AI2::class.java, true))
        assertAnnotatedWith(AI2::class.java, reflections.getTypesAnnotatedWith(AI2::class.java, true))

        assertToStringEquals(setOf(C1::class.java, C2::class.java, C3::class.java, C5::class.java),
                             reflections.getTypesAnnotatedWith(AC1::class.java, true))
        assertAnnotatedWith(AC1::class.java, reflections.getTypesAnnotatedWith(AC1::class.java, true))

        assertToStringEquals(setOf(C1::class.java), reflections.getTypesAnnotatedWith(AC1n::class.java, true))
        assertAnnotatedWith(AC1n::class.java, reflections.getTypesAnnotatedWith(AC1n::class.java, true))

        assertToStringEquals(setOf(AI1::class.java,
                                   I1::class.java,
                                   I2::class.java,
                                   C1::class.java,
                                   C2::class.java,
                                   C3::class.java,
                                   C5::class.java), reflections.getTypesAnnotatedWith(MAI1::class.java))
        assertMetaAnnotatedWith(MAI1::class.java, reflections.getTypesAnnotatedWith(MAI1::class.java))

        assertToStringEquals(setOf(I1::class.java,
                                   I2::class.java,
                                   C1::class.java,
                                   C2::class.java,
                                   C3::class.java,
                                   C5::class.java), reflections.getTypesAnnotatedWith(AI1::class.java))
        assertMetaAnnotatedWith(AI1::class.java, reflections.getTypesAnnotatedWith(AI1::class.java))

        assertToStringEquals(setOf(I2::class.java, C1::class.java, C2::class.java, C3::class.java, C5::class.java),
                             reflections.getTypesAnnotatedWith(AI2::class.java))
        assertMetaAnnotatedWith(AI2::class.java, reflections.getTypesAnnotatedWith(AI2::class.java))

        assertToStringEquals(emptySet<Class<*>>(), reflections.getTypesAnnotatedWith(AM1::class.java))

        //annotation member value matching
        val ac2 = JavaSpecific.newAC2("ugh?!")

        assertToStringEquals(setOf(C3::class.java,
                                   C5::class.java,
                                   I3::class.java,
                                   C6::class.java,
                                   AC3::class.java,
                                   C7::class.java), reflections.getTypesAnnotatedWith(ac2))

        assertToStringEquals(setOf(C3::class.java, I3::class.java, AC3::class.java),
                             reflections.getTypesAnnotatedWith(ac2, true))
    }

    @Test
    fun testMethodsAnnotatedWith() {
        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("m1"),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Int::class.javaPrimitiveType,
                                                                    Array<String>::class.java),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Array<IntArray>::class.java,
                                                                    Array<Array<String>>::class.java),
                                   C4::class.java.getDeclaredMethod("m3")),
                             reflections.methodsAnnotatedWith(AM1::class.java))

        val am1 = JavaSpecific.newAM1("1")
        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("m1"),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Int::class.javaPrimitiveType,
                                                                    Array<String>::class.java),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Array<IntArray>::class.java,
                                                                    Array<Array<String>>::class.java)),
                             reflections.methodsAnnotatedWith(am1))
    }

    @Test
    fun testConstructorsAnnotatedWith() {
        assertToStringEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                             reflections.constructorsAnnotatedWith(AM1::class.java))

        val am1 = JavaSpecific.newAM1("1")
        assertToStringEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                             reflections.constructorsAnnotatedWith(am1))
    }

    @Test
    fun testFieldsAnnotatedWith() {
        assertToStringEquals(setOf(C4::class.java.getDeclaredField("f1"), C4::class.java.getDeclaredField("f2")),
                             reflections.fieldsAnnotatedWith(AF1::class.java))

        assertToStringEquals(setOf(C4::class.java.getDeclaredField("f2")),
                             reflections.fieldsAnnotatedWith(JavaSpecific.newAF1("2")))
    }

    @Test
    fun testMethodParameter() {
        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("m4", String::class.java),
                                   Usage.C1::class.java.getDeclaredMethod("method", String::class.java)),
                             reflections.methodsMatchParams(String::class.java),
                             sortBy = Any::toString)

        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("m1"),
                                   C4::class.java.getDeclaredMethod("m3"),
                                   AC2::class.java.getMethod("value"),
                                   AF1::class.java.getMethod("value"),
                                   AM1::class.java.getMethod("value"),
                                   Usage.C1::class.java.getDeclaredMethod("method"),
                                   Usage.C2::class.java.getDeclaredMethod("method")),
                             reflections.methodsMatchParams(),
                             sortBy = Any::toString)

        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("m1",
                                                                    Array<IntArray>::class.java,
                                                                    Array<Array<String>>::class.java)),
                             reflections.methodsMatchParams(Array<IntArray>::class.java,
                                                            Array<Array<String>>::class.java),
                             sortBy = Any::toString)

        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("add",
                                                                    Int::class.javaPrimitiveType,
                                                                    Int::class.javaPrimitiveType)),
                             reflections.methodsReturn(Int::class.javaPrimitiveType!!),
                             sortBy = Any::toString)

        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("m3"),
                                   C4::class.java.getDeclaredMethod("m4", String::class.java),
                                   AC2::class.java.getMethod("value"),
                                   AF1::class.java.getMethod("value"),
                                   AM1::class.java.getMethod("value")),
                             reflections.methodsReturn(String::class.java),
                             sortBy = Any::toString)

        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("m1"),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Int::class.javaPrimitiveType,
                                                                    Array<String>::class.java),
                                   C4::class.java.getDeclaredMethod("m1",
                                                                    Array<IntArray>::class.java,
                                                                    Array<Array<String>>::class.java),
                                   Usage.C1::class.java.getDeclaredMethod("method"),
                                   Usage.C1::class.java.getDeclaredMethod("method", String::class.java),
                                   Usage.C2::class.java.getDeclaredMethod("method")),
                             reflections.methodsReturn(Void.TYPE),
                             sortBy = Any::toString)

        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("m4", String::class.java)),
                             reflections.methodsWithAnyParamAnnotated(AM1::class.java))

        assertToStringEquals(setOf(C4::class.java.getDeclaredMethod("m4", String::class.java)),
                             reflections.methodsWithAnyParamAnnotated(JavaSpecific.newAM1("2")))
    }

    @Test
    fun testConstructorParameter() {
        assertToStringEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                             reflections.constructorsMatchParams(String::class.java))

        assertToStringEquals(setOf(C1::class.java.getDeclaredConstructor(),
                                   C2::class.java.getDeclaredConstructor(),
                                   C3::class.java.getDeclaredConstructor(),
                                   C4::class.java.getDeclaredConstructor(),
                                   C5::class.java.getDeclaredConstructor(),
                                   C6::class.java.getDeclaredConstructor(),
                                   C7::class.java.getDeclaredConstructor(),
                                   Usage.C1::class.java.getDeclaredConstructor(),
                                   Usage.C2::class.java.getDeclaredConstructor()),
                             reflections.constructorsMatchParams(),
                             sortBy = Any::toString)

        assertToStringEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                             reflections.constructorsWithAnyParamAnnotated(AM1::class.java))

        assertToStringEquals(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                             reflections.constructorsWithAnyParamAnnotated(JavaSpecific.newAM1("1")))
    }

    @Test
    open fun testResourcesScanner() {
        val filter = Filter.Composite(listOf(Include(".*\\.xml"), Exclude(".*testModel-reflections\\.xml")))
        val configuration = Configuration()
        configuration.filter = filter
        configuration.scanners = arrayOf(ResourcesScanner()).toSet()
        configuration.urls = listOfNotNull(urlForClass(TestModel::class.java)).toMutableSet()
        val reflections = Reflections(configuration)

        val resolved = reflections.resources(Pattern.compile(".*resource1-reflections\\.xml"))
        assertToStringEquals(setOf(Datum("META-INF/reflections/resource1-reflections.xml")), resolved)

        val resources = reflections.ask<ResourcesScanner, Datum> { keys() }
        assertToStringEquals(setOf(Datum("resource1-reflections.xml"), Datum("resource2-reflections.xml")),
                             resources,
                             sortBy = Any::toString)
    }

    @Test
    fun testMethodParameterNames() {
        assertToStringEquals(emptyList<Any>(), reflections.paramNames(C4::class.java.getDeclaredMethod("m3")))

        assertToStringEquals(listOf("string"),
                             reflections.paramNames(C4::class.java.getDeclaredMethod("m4", String::class.java)))

        assertToStringEquals(listOf("i1", "i2"),
                             reflections.paramNames(C4::class.java.getDeclaredMethod("add",
                                                                                     Int::class.javaPrimitiveType,
                                                                                     Int::class.javaPrimitiveType)))

        assertToStringEquals(listOf("f1"),
                             reflections.paramNames(C4::class.java.getDeclaredConstructor(String::class.java)))
    }

    @Test
    fun testMemberUsageScanner() {
        //field usage
        assertToStringEquals(setOf(Usage.C1::class.java.getDeclaredConstructor(),
                                   Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java),
                                   Usage.C1::class.java.getDeclaredMethod("method"),
                                   Usage.C1::class.java.getDeclaredMethod("method", String::class.java)),
                             reflections.usages(Usage.C1::class.java.getDeclaredField("c2")))

        //method usage
        assertToStringEquals(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                             reflections.usages(Usage.C1::class.java.getDeclaredMethod("method")))

        assertToStringEquals(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                             reflections.usages(Usage.C1::class.java.getDeclaredMethod("method", String::class.java)))

        //constructor usage
        assertToStringEquals(setOf(Usage.C2::class.java.getDeclaredConstructor(),
                                   Usage.C2::class.java.getDeclaredMethod("method")),
                             reflections.usages(Usage.C1::class.java.getDeclaredConstructor()))

        assertToStringEquals(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                             reflections.usages(Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java)))
    }

    @Test
    fun testScannerNotConfigured() {
        try {
            Reflections(Configuration(filter = Filter.Composite(listOf(TestModelFilter,
                                                                       Filter.Include(TestModel::class.java.toPackageNameRegex()))),
                                      urls = listOfNotNull(urlForClass(TestModel::class.java)).toSet())).methodsAnnotatedWith(
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
            println(JsonSerializer.toString(reflections))
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
