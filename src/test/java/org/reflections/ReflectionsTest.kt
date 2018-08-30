package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class ReflectionsTest {
    val TestModelFilter = Include("org.reflections.TestModel\\$.*")

    lateinit var configuration: Configuration

    @BeforeAll
    open fun setup() {
        configuration =
                Configuration(scanners = setOf(SubTypesScanner(false),
                                               TypeAnnotationsScanner(),
                                               FieldAnnotationsScanner(),
                                               MethodAnnotationsScanner(),
                                               MethodParameterScanner(),
                                               MethodParameterNamesScanner(),
                                               MemberUsageScanner()),
                              filter = TestModelFilter,
                              urls = setOf(urlForClass(TestModel::class.java)!!)).withScan()
        println(JsonSerializer.toString(configuration))
    }

    @Nested
    inner class AllTypes {
        @Test
        fun `is not empty`() {
            assertFalse(configuration.allTypes().isEmpty(),
                        "allTypes should not be empty when Reflections is configured with SubTypesScanner(false)")
        }
    }

    @Nested
    inner class SubTypesOf {
        @Test
        fun `interface`() {
            assertToStringEqualsSorted(setOf(I2::class.java,
                                             C1::class.java,
                                             C2::class.java,
                                             C3::class.java,
                                             C5::class.java), configuration.subTypesOf(I1::class.java))
        }

        @Test
        fun `class`() {
            assertToStringEqualsSorted(setOf(C2::class.java, C3::class.java, C5::class.java),
                                       configuration.subTypesOf(C1::class.java))
        }
    }

    @Nested
    inner class TypesAnnotatedWith {
        @Nested
        inner class MetaEnabled {
            @Test
            fun `annotation on class`() {
                assertTypesAnnotatedWith(AC1n::class.java, setOf(C1::class.java))
            }

            @Test
            fun `meta annotation on class`() {
                assertTypesAnnotatedWith(AC1::class.java,
                                         setOf(C1::class.java, C2::class.java, C3::class.java, C5::class.java))
            }

            @Test
            fun `annotation on interface`() {
                assertTypesAnnotatedWith(AI2::class.java, setOf(I2::class.java))
            }

            @Test
            fun `meta annotation on interface`() {
                assertTypesAnnotatedWith(MAI1::class.java, setOf(AI1::class.java))
            }

            private fun assertTypesAnnotatedWith(annotation: Class<out Annotation>, expected: Set<Class<*>>) {
                val actual = configuration.typesAnnotatedWith(annotation, true)
                assertToStringEqualsSorted(expected, actual)
                assertTrue(actual.all { it.annotations.asList().map { it.annotationType() }.contains(annotation) })
            }

            @Test
            fun `value match`() {
                assertToStringEqualsSorted(setOf(C3::class.java, I3::class.java, AC3::class.java),
                                           configuration.typesAnnotatedWith(JavaSpecific.newAC2("ugh?!"), true))
            }
        }

        @Nested
        inner class MetaDisabled {
            @Test
            fun `meta-annotated annotation on interface`() {
                assertMetaAnnotatedWith(AI1::class.java,
                                        setOf(I1::class.java,
                                              I2::class.java,
                                              C1::class.java,
                                              C2::class.java,
                                              C3::class.java,
                                              C5::class.java))
            }

            @Test
            fun `meta annotation on interface`() {
                assertMetaAnnotatedWith(MAI1::class.java,
                                        setOf(AI1::class.java,
                                              I1::class.java,
                                              I2::class.java,
                                              C1::class.java,
                                              C2::class.java,
                                              C3::class.java,
                                              C5::class.java))
                assertMetaAnnotatedWith(AI2::class.java,
                                        setOf(I2::class.java,
                                              C1::class.java,
                                              C2::class.java,
                                              C3::class.java,
                                              C5::class.java))

            }

            private fun assertMetaAnnotatedWith(annotation: Class<out Annotation>, expected: Set<Class<out Any>>) {
                val actual = configuration.typesAnnotatedWith(annotation, false)
                assertToStringEqualsSorted(expected, actual)
                assertTrue(actual.all {
                    val result = mutableSetOf<Class<*>>()
                    val stack = it.classAndInterfaceHieararchyExceptObject().toMutableList()
                    while (!stack.isEmpty()) {
                        val next = stack.removeAt(0)
                        if (result.add(next)) {
                            stack += next.declaredAnnotations.asList().map { it.annotationType() }.filter {
                                !result.contains(it) && !stack.contains(it)
                            }
                        }
                    }
                    result.contains(annotation)
                })

            }
        }

        @Test
        fun `value match`() {
            assertToStringEqualsSorted(setOf(C3::class.java,
                                             C5::class.java,
                                             I3::class.java,
                                             C6::class.java,
                                             AC3::class.java,
                                             C7::class.java),
                                       configuration.typesAnnotatedWith(JavaSpecific.newAC2("ugh?!"), false))
        }

        @Test
        fun `no match`() {
            assertToStringEqualsSorted(emptySet(), configuration.typesAnnotatedWith(AM1::class.java, false))
        }

    }


    @Nested
    inner class MethodsAnnotatedWith {
        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m1"),
                                             C4::class.java.getDeclaredMethod("m1",
                                                                              Int::class.javaPrimitiveType,
                                                                              Array<String>::class.java),
                                             C4::class.java.getDeclaredMethod("m1",
                                                                              Array<IntArray>::class.java,
                                                                              Array<Array<String>>::class.java)),
                                       configuration.methodsAnnotatedWith(JavaSpecific.newAM1("1")))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m1"),
                                             C4::class.java.getDeclaredMethod("m1",
                                                                              Int::class.javaPrimitiveType,
                                                                              Array<String>::class.java),
                                             C4::class.java.getDeclaredMethod("m1",
                                                                              Array<IntArray>::class.java,
                                                                              Array<Array<String>>::class.java),
                                             C4::class.java.getDeclaredMethod("m3")),
                                       configuration.methodsAnnotatedWith(AM1::class.java))
        }
    }

    @Nested
    inner class ConstructorsAnnotatedWith {
        @Test
        fun `by isntance`() {
            val am1 = JavaSpecific.newAM1("1")
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       configuration.constructorsAnnotatedWith(am1))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       configuration.constructorsAnnotatedWith(AM1::class.java))
        }
    }

    @Nested
    inner class FieldsAnnotatedWith {
        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredField("f2")),
                                       configuration.fieldsAnnotatedWith(JavaSpecific.newAF1("2")))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredField("f1"),
                                             C4::class.java.getDeclaredField("f2")),
                                       configuration.fieldsAnnotatedWith(AF1::class.java))
        }
    }

    @Nested
    inner class MethodsMatchParams {
        @Test
        fun `multiple nested array params`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m1",
                                                                              Array<IntArray>::class.java,
                                                                              Array<Array<String>>::class.java)),
                                       configuration.methodsMatchParams(Array<IntArray>::class.java,
                                                                        Array<Array<String>>::class.java))
        }

        @Test
        fun `no param`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m1"),
                                             C4::class.java.getDeclaredMethod("m3"),
                                             AC2::class.java.getMethod("value"),
                                             AF1::class.java.getMethod("value"),
                                             AM1::class.java.getMethod("value"),
                                             Usage.C1::class.java.getDeclaredMethod("method"),
                                             Usage.C2::class.java.getDeclaredMethod("method")),
                                       configuration.methodsMatchParams())
        }

        @Test
        fun `single simple param`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m4", String::class.java),
                                             Usage.C1::class.java.getDeclaredMethod("method", String::class.java)),
                                       configuration.methodsMatchParams(String::class.java))
        }
    }

    @Nested
    inner class MethodsReturns {
        @Test
        fun void() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m1"),
                                             C4::class.java.getDeclaredMethod("m1",
                                                                              Int::class.javaPrimitiveType,
                                                                              Array<String>::class.java),
                                             C4::class.java.getDeclaredMethod("m1",
                                                                              Array<IntArray>::class.java,
                                                                              Array<Array<String>>::class.java),
                                             Usage.C1::class.java.getDeclaredMethod("method"),
                                             Usage.C1::class.java.getDeclaredMethod("method", String::class.java),
                                             Usage.C2::class.java.getDeclaredMethod("method")),
                                       configuration.methodsReturn(Void.TYPE))
        }

        @Test
        fun `non-primitive`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m3"),
                                             C4::class.java.getDeclaredMethod("m4", String::class.java),
                                             AC2::class.java.getMethod("value"),
                                             AF1::class.java.getMethod("value"),
                                             AM1::class.java.getMethod("value")),
                                       configuration.methodsReturn(String::class.java))
        }

        @Test
        fun primitive() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("add",
                                                                              Int::class.javaPrimitiveType,
                                                                              Int::class.javaPrimitiveType)),
                                       configuration.methodsReturn(Int::class.javaPrimitiveType!!))
        }
    }

    @Nested
    inner class MethodsWithAnyParamAnnotated {
        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m4", String::class.java)),
                                       configuration.methodsWithAnyParamAnnotated(JavaSpecific.newAM1("2")))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m4", String::class.java)),
                                       configuration.methodsWithAnyParamAnnotated(AM1::class.java))
        }
    }

    @Nested
    inner class ConstructorMatchParams {
        @Test
        fun `no params`() {
            assertToStringEqualsSorted(setOf(C1::class.java.getDeclaredConstructor(),
                                             C2::class.java.getDeclaredConstructor(),
                                             C3::class.java.getDeclaredConstructor(),
                                             C4::class.java.getDeclaredConstructor(),
                                             C5::class.java.getDeclaredConstructor(),
                                             C6::class.java.getDeclaredConstructor(),
                                             C7::class.java.getDeclaredConstructor(),
                                             Usage.C1::class.java.getDeclaredConstructor(),
                                             Usage.C2::class.java.getDeclaredConstructor()),
                                       configuration.constructorsMatchParams())
        }

        @Test
        fun `single param`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       configuration.constructorsMatchParams(String::class.java))
        }
    }

    @Nested
    inner class ConstructorsWithAnyParamAnnotated {
        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       configuration.constructorsWithAnyParamAnnotated(JavaSpecific.newAM1("1")))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       configuration.constructorsWithAnyParamAnnotated(AM1::class.java))
        }
    }

    @Nested
    inner class ParamNames {
        @Test
        fun ctor() {
            assertToStringEqualsSorted(listOf("f1"),
                                       configuration.paramNames(C4::class.java.getDeclaredConstructor(String::class.java)))
        }

        @Test
        fun `multiple params`() {
            assertToStringEqualsSorted(listOf("i1", "i2"),
                                       configuration.paramNames(C4::class.java.getDeclaredMethod("add",
                                                                                                 Int::class.javaPrimitiveType,
                                                                                                 Int::class.javaPrimitiveType)))
        }

        @Test
        fun `single param`() {
            assertToStringEqualsSorted(listOf("string"),
                                       configuration.paramNames(C4::class.java.getDeclaredMethod("m4",
                                                                                                 String::class.java)))
        }

        @Test
        fun `no param`() {
            assertToStringEqualsSorted(emptyList<Any>(),
                                       configuration.paramNames(C4::class.java.getDeclaredMethod("m3")))
        }
    }

    @Nested
    inner class Usages {
        @Test
        fun `ctor - with param`() {
            assertToStringEqualsSorted(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                                       configuration.usages(Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java)))
        }

        @Test
        fun `ctor - no param`() {
            assertToStringEqualsSorted(setOf(Usage.C2::class.java.getDeclaredConstructor(),
                                             Usage.C2::class.java.getDeclaredMethod("method")),
                                       configuration.usages(Usage.C1::class.java.getDeclaredConstructor()))
        }

        @Test
        fun `method - with param`() {
            assertToStringEqualsSorted(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                                       configuration.usages(Usage.C1::class.java.getDeclaredMethod("method",
                                                                                                   String::class.java)))
        }

        @Test
        fun `method - no param`() {
            assertToStringEqualsSorted(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                                       configuration.usages(Usage.C1::class.java.getDeclaredMethod("method")))
        }

        @Test
        fun field() {
            assertToStringEqualsSorted(setOf(Usage.C1::class.java.getDeclaredConstructor(),
                                             Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java),
                                             Usage.C1::class.java.getDeclaredMethod("method"),
                                             Usage.C1::class.java.getDeclaredMethod("method", String::class.java)),
                                       configuration.usages(Usage.C1::class.java.getDeclaredField("c2")))
        }
    }

    @Test
    open fun resources() {
        val filter = Filter.Composite(listOf(Include(".*\\.xml"), Exclude(".*testModel-reflections\\.xml")))
        val configuration =
                Configuration(scanners = setOf(ResourcesScanner()),
                              filter = filter,
                              urls = setOf(urlForClass(TestModel::class.java)!!)).withScan()

        val resolved = configuration.resources(Pattern.compile(".*resource1-reflections\\.xml"))
        assertToStringEqualsSorted(setOf(Datum("META-INF/reflections/resource1-reflections.xml")), resolved)

        val resources = configuration.ask<ResourcesScanner, Datum> { keys() }
        assertToStringEqualsSorted(setOf(Datum("resource1-reflections.xml"), Datum("resource2-reflections.xml")),
                                   resources)
    }


    @Test
    fun `MethodAnnotationsScanner not configured`() {
        val e = assertThrows<RuntimeException> {
            Configuration(scanners = setOf(TypeAnnotationsScanner(), SubTypesScanner()),
                          filter = Filter.Composite(listOf(TestModelFilter,
                                                           Filter.Include(TestModel::class.java.toPackageNameRegex()))),
                          urls = listOfNotNull(urlForClass(TestModel::class.java)).toSet()).withScan()
                .methodsAnnotatedWith(AC1::class.java)
        }
        assertEquals("MethodAnnotationsScanner was not configured", e.message)
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