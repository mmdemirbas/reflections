package org.reflections

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.reflections.TestModel.AC2

class TypeAnnotationsScannerTest {
    @Nested
    inner class TypesAnnotatedWith {
        private val scanner =
                CompositeScanner(SubTypesScanner(excludeObjectClass = false),
                                 TypeAnnotationsScanner()).scan(TestModel::class.java).dump()

        @Nested
        inner class MetaEnabled {
            @Test
            fun `annotation on class`() {
                assertTypesAnnotatedWith(TestModel.AC1n::class.java, setOf(TestModel.C1::class.java))
            }

            @Test
            fun `meta annotation on class`() {
                assertTypesAnnotatedWith(TestModel.AC1::class.java,
                                         setOf(TestModel.C1::class.java,
                                               TestModel.C2::class.java,
                                               TestModel.C3::class.java,
                                               TestModel.C5::class.java))
            }

            @Test
            fun `annotation on interface`() {
                assertTypesAnnotatedWith(TestModel.AI2::class.java, setOf(TestModel.I2::class.java))
            }

            @Test
            fun `meta annotation on interface`() {
                assertTypesAnnotatedWith(TestModel.MAI1::class.java, setOf(TestModel.AI1::class.java))
            }

            private fun assertTypesAnnotatedWith(annotation: Class<out Annotation>, expected: Set<Class<*>>) {
                val actual = scanner.typesAnnotatedWith(annotation, true).toSet()
                assertToStringEqualsSorted(expected, actual)
                Assertions.assertTrue(actual.all {
                    it.annotations.asList().map { it.annotationType() }.contains(annotation)
                })
            }

            @Test
            fun `value match`() {
                assertToStringEqualsSorted(setOf(TestModel.C3::class.java,
                                                 TestModel.I3::class.java,
                                                 TestModel.AC3::class.java),
                                           scanner.typesAnnotatedWith(AC2::class.newAnnotation(Pair("value", "ugh?!")),
                                                                      true))
            }
        }

        @Nested
        inner class MetaDisabled {
            @Test
            fun `meta-annotated annotation on interface`() {
                assertMetaAnnotatedWith(TestModel.AI1::class.java,
                                        setOf(TestModel.I1::class.java,
                                              TestModel.I2::class.java,
                                              TestModel.C1::class.java,
                                              TestModel.C2::class.java,
                                              TestModel.C3::class.java,
                                              TestModel.C5::class.java))
            }

            @Test
            fun `meta annotation on interface`() {
                assertMetaAnnotatedWith(TestModel.MAI1::class.java,
                                        setOf(TestModel.AI1::class.java,
                                              TestModel.I1::class.java,
                                              TestModel.I2::class.java,
                                              TestModel.C1::class.java,
                                              TestModel.C2::class.java,
                                              TestModel.C3::class.java,
                                              TestModel.C5::class.java))
                assertMetaAnnotatedWith(TestModel.AI2::class.java,
                                        setOf(TestModel.I2::class.java,
                                              TestModel.C1::class.java,
                                              TestModel.C2::class.java,
                                              TestModel.C3::class.java,
                                              TestModel.C5::class.java))

            }

            // todo: gereksiz toList, toSet ve asList gibi metotları kaldır. mesela sonrasında filter olanlar...

            private fun assertMetaAnnotatedWith(annotation: Class<out Annotation>, expected: Set<Class<out Any>>) {
                val actual = scanner.typesAnnotatedWith(annotation, false).toSet()
                assertToStringEqualsSorted(expected, actual)
                Assertions.assertTrue(actual.all { cls ->
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
            assertToStringEqualsSorted(setOf(TestModel.C3::class.java,
                                             TestModel.C5::class.java,
                                             TestModel.I3::class.java,
                                             TestModel.C6::class.java,
                                             TestModel.AC3::class.java,
                                             TestModel.C7::class.java),
                                       scanner.typesAnnotatedWith(AC2::class.newAnnotation(Pair("value", "ugh?!")),
                                                                  false))
        }

        @Test
        fun `no match`() {
            assertToStringEqualsSorted(emptySet(), scanner.typesAnnotatedWith(TestModel.AM1::class.java, false))
        }

        @Test
        fun `TypeAnnotationsScanner not configured`() {
            val e = assertThrows<RuntimeException> {
                CompositeScanner().scan(TestModel::class.java).typesAnnotatedWith(TestModel.AC1::class.java, false)
            }
            Assertions.assertEquals("TypeAnnotationsScanner was not configured", e.message)
        }
    }
}