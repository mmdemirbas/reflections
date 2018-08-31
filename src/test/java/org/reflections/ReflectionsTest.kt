package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
import java.util.regex.Pattern

class ReflectionsTest {
    @Nested
    inner class AllTypes {

        @Test
        fun `object included`() {
            assertTrue(SubTypesScanner(excludeObjectClass = false).scan(TestModel::class.java).allTypes().isNotEmpty())
        }

        @Test
        fun `object excluded`() {
            assertTrue(SubTypesScanner(excludeObjectClass = true).scan(TestModel::class.java).allTypes().isEmpty())
        }
    }

    @Nested
    inner class SubTypesOf {
        private val scanner = SubTypesScanner(excludeObjectClass = false).scan(TestModel::class.java)

        @Test
        fun `interface`() {
            assertToStringEqualsSorted(setOf(I2::class.java,
                                             C1::class.java,
                                             C2::class.java,
                                             C3::class.java,
                                             C5::class.java), scanner.subTypesOf(I1::class.java))
        }

        @Test
        fun `class`() {
            assertToStringEqualsSorted(setOf(C2::class.java, C3::class.java, C5::class.java),
                                       scanner.subTypesOf(C1::class.java))
        }
    }

    @Nested
    inner class TypesAnnotatedWith {
        private val scanner =
                CompositeScanner(SubTypesScanner(excludeObjectClass = false),
                                 TypeAnnotationsScanner()).scan(TestModel::class.java)

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
                val actual = scanner.typesAnnotatedWith(annotation, true)
                assertToStringEqualsSorted(expected, actual)
                assertTrue(actual.all { it.annotations.asList().map { it.annotationType() }.contains(annotation) })
            }

            @Test
            fun `value match`() {
                assertToStringEqualsSorted(setOf(C3::class.java, I3::class.java, AC3::class.java),
                                           scanner.typesAnnotatedWith(JavaSpecific.newAC2("ugh?!"), true))
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

            // todo: gereksiz toList, toSet ve asList gibi metotları kaldır. mesela sonrasında filter olanlar...

            private fun assertMetaAnnotatedWith(annotation: Class<out Annotation>, expected: Set<Class<out Any>>) {
                val actual = scanner.typesAnnotatedWith(annotation, false)
                assertToStringEqualsSorted(expected, actual)
                assertTrue(actual.all { cls ->
                    val result = mutableSetOf<Class<*>>()
                    val stack = cls.classAndInterfaceHieararchyExceptObject().toMutableList()
                    while (!stack.isEmpty()) {
                        val next = stack.removeAt(0)
                        if (result.add(next)) {
                            stack += next.declaredAnnotations.map { it.annotationType() }.filter {
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
                                       scanner.typesAnnotatedWith(JavaSpecific.newAC2("ugh?!"), false))
        }

        @Test
        fun `no match`() {
            assertToStringEqualsSorted(emptySet(), scanner.typesAnnotatedWith(AM1::class.java, false))
        }

        @Test
        fun `TypeAnnotationsScanner not configured`() {
            val e = assertThrows<RuntimeException> {
                CompositeScanner().scan(TestModel::class.java).typesAnnotatedWith(AC1::class.java, false)
            }
            assertEquals("TypeAnnotationsScanner was not configured", e.message)
        }
    }


    @Nested
    inner class MethodsAnnotatedWith {
        private val scanner = MethodAnnotationsScanner().scan(TestModel::class.java)

        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m1"),
                                             C4::class.java.getDeclaredMethod("m1",
                                                                              Int::class.javaPrimitiveType,
                                                                              Array<String>::class.java),
                                             C4::class.java.getDeclaredMethod("m1",
                                                                              Array<IntArray>::class.java,
                                                                              Array<Array<String>>::class.java)),
                                       scanner.methodsAnnotatedWith(JavaSpecific.newAM1("1")))
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
                                       scanner.methodsAnnotatedWith(AM1::class.java))
        }
    }

    @Nested
    inner class ConstructorsAnnotatedWith {
        private val scanner = MethodAnnotationsScanner().scan(TestModel::class.java)

        @Test
        fun `by isntance`() {
            val am1 = JavaSpecific.newAM1("1")
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsAnnotatedWith(am1))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsAnnotatedWith(AM1::class.java))
        }
    }

    @Nested
    inner class FieldsAnnotatedWith {
        private val scanner = FieldAnnotationsScanner().scan(TestModel::class.java)

        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredField("f2")),
                                       scanner.fieldsAnnotatedWith(JavaSpecific.newAF1("2")))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredField("f1"),
                                             C4::class.java.getDeclaredField("f2")),
                                       scanner.fieldsAnnotatedWith(AF1::class.java))
        }
    }

    @Nested
    inner class MethodsMatchParams {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java)

        @Test
        fun `multiple nested array params`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m1",
                                                                              Array<IntArray>::class.java,
                                                                              Array<Array<String>>::class.java)),
                                       scanner.methodsWithParamTypes(Array<IntArray>::class.java,
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
                                       scanner.methodsWithParamTypes())
        }

        @Test
        fun `single simple param`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m4", String::class.java),
                                             Usage.C1::class.java.getDeclaredMethod("method", String::class.java)),
                                       scanner.methodsWithParamTypes(String::class.java))
        }
    }

    @Nested
    inner class MethodsReturns {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java)

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
                                       scanner.methodsWithReturnType(Void.TYPE))
        }

        @Test
        fun `non-primitive`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m3"),
                                             C4::class.java.getDeclaredMethod("m4", String::class.java),
                                             AC2::class.java.getMethod("value"),
                                             AF1::class.java.getMethod("value"),
                                             AM1::class.java.getMethod("value")),
                                       scanner.methodsWithReturnType(String::class.java))
        }

        @Test
        fun primitive() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("add",
                                                                              Int::class.javaPrimitiveType,
                                                                              Int::class.javaPrimitiveType)),
                                       scanner.methodsWithReturnType(Int::class.javaPrimitiveType!!))
        }
    }

    @Nested
    inner class MethodsWithAnyParamAnnotated {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java)

        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m4", String::class.java)),
                                       scanner.methodsWithAnyParamAnnotated(JavaSpecific.newAM1("2")))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredMethod("m4", String::class.java)),
                                       scanner.methodsWithAnyParamAnnotated(AM1::class.java))
        }
    }

    @Nested
    inner class ConstructorMatchParams {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java)

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
                                       scanner.constructorsWithParamTypes())
        }

        @Test
        fun `single param`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsWithParamTypes(String::class.java))
        }
    }

    @Nested
    inner class ConstructorsWithAnyParamAnnotated {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java)

        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsWithAnyParamAnnotated(JavaSpecific.newAM1("1")))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsWithAnyParamAnnotated(AM1::class.java))
        }
    }

    @Nested
    inner class ParamNames {
        private val scanner = MethodParameterNamesScanner().scan(TestModel::class.java)

        @Test
        fun ctor() {
            assertToStringEqualsSorted(listOf("f1"),
                                       scanner.paramNames(C4::class.java.getDeclaredConstructor(String::class.java)))
        }

        @Test
        fun `multiple params`() {
            assertToStringEqualsSorted(listOf("i1", "i2"),
                                       scanner.paramNames(C4::class.java.getDeclaredMethod("add",
                                                                                           Int::class.javaPrimitiveType,
                                                                                           Int::class.javaPrimitiveType)))
        }

        @Test
        fun `single param`() {
            assertToStringEqualsSorted(listOf("string"),
                                       scanner.paramNames(C4::class.java.getDeclaredMethod("m4", String::class.java)))
        }

        @Test
        fun `no param`() {
            assertToStringEqualsSorted(emptyList<Any>(), scanner.paramNames(C4::class.java.getDeclaredMethod("m3")))
        }
    }

    @Nested
    inner class Usages {
        private val scanner = MemberUsageScanner().scan(TestModel::class.java)

        @Test
        fun `ctor - with param`() {
            assertToStringEqualsSorted(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                                       scanner.usages(Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java)))
        }

        @Test
        fun `ctor - no param`() {
            assertToStringEqualsSorted(setOf(Usage.C2::class.java.getDeclaredConstructor(),
                                             Usage.C2::class.java.getDeclaredMethod("method")),
                                       scanner.usages(Usage.C1::class.java.getDeclaredConstructor()))
        }

        @Test
        fun `method - with param`() {
            assertToStringEqualsSorted(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                                       scanner.usages(Usage.C1::class.java.getDeclaredMethod("method",
                                                                                             String::class.java)))
        }

        @Test
        fun `method - no param`() {
            assertToStringEqualsSorted(setOf(Usage.C2::class.java.getDeclaredMethod("method")),
                                       scanner.usages(Usage.C1::class.java.getDeclaredMethod("method")))
        }

        @Test
        fun field() {
            assertToStringEqualsSorted(setOf(Usage.C1::class.java.getDeclaredConstructor(),
                                             Usage.C1::class.java.getDeclaredConstructor(Usage.C2::class.java),
                                             Usage.C1::class.java.getDeclaredMethod("method"),
                                             Usage.C1::class.java.getDeclaredMethod("method", String::class.java)),
                                       scanner.usages(Usage.C1::class.java.getDeclaredField("c2")))
        }
    }

    @Nested
    inner class Resources {
        @Test
        fun resources1() {
            val scanner =
                    ResourceScanner().scan(filter = Filter.Composite(listOf(Include(".*\\.xml"),
                                                                            Exclude(".*testModel-reflections\\.xml"))),
                                           urls = setOf(urlForClass(TestModel::class.java)!!))

            assertToStringEqualsSorted(setOf("META-INF/reflections/resource1-reflections.xml"),
                                       scanner.resources(Pattern.compile(".*resource1-reflections\\.xml")))

            assertToStringEqualsSorted(setOf("resource1-reflections.xml", "resource2-reflections.xml"),
                                       scanner.resources())
        }


        @Test
        fun resources2() {
            val scanner =
                    ResourceScanner().scan(filter = Filter.Composite(listOf(Include(".*\\.xml"), Include(".*\\.json"))),
                                           urls = setOf(urlForClass(TestModel::class.java)!!))

            assertToStringEqualsSorted(setOf("META-INF/reflections/resource1-reflections.xml"),
                                       scanner.resources(Pattern.compile(".*resource1-reflections\\.xml")))

            assertToStringEqualsSorted(setOf("resource1-reflections.xml",
                                             "resource2-reflections.xml",
                                             "testModel-reflections.xml",
                                             "testModel-reflections.json"), scanner.resources())
        }
    }
}
