package org.reflections

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.reflections.TestModel.AM1

class MethodParameterScannerTest {
    @Nested
    inner class MethodsMatchParams {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java).dump()

        @Test
        fun `multiple nested array params`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m1",
                                                                                        Array<IntArray>::class.java,
                                                                                        Array<Array<String>>::class.java)),
                                       scanner.methodsWithParamTypes(Array<IntArray>::class.java,
                                                                     Array<Array<String>>::class.java))
        }

        @Test
        fun `no param`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m1"),
                                             TestModel.C4::class.java.getDeclaredMethod("m3"),
                                             TestModel.AC2::class.java.getMethod("value"),
                                             TestModel.AF1::class.java.getMethod("value"),
                                             TestModel.AM1::class.java.getMethod("value"),
                                             TestModel.Usage.C1::class.java.getDeclaredMethod("method"),
                                             TestModel.Usage.C2::class.java.getDeclaredMethod("method")),
                                       scanner.methodsWithParamTypes())
        }

        @Test
        fun `single simple param`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m4", String::class.java),
                                             TestModel.Usage.C1::class.java.getDeclaredMethod("method",
                                                                                              String::class.java)),
                                       scanner.methodsWithParamTypes(String::class.java))
        }
    }

    @Nested
    inner class MethodsReturns {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java).dump()

        @Test
        fun void() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m1"),
                                             TestModel.C4::class.java.getDeclaredMethod("m1",
                                                                                        Int::class.javaPrimitiveType,
                                                                                        Array<String>::class.java),
                                             TestModel.C4::class.java.getDeclaredMethod("m1",
                                                                                        Array<IntArray>::class.java,
                                                                                        Array<Array<String>>::class.java),
                                             TestModel.Usage.C1::class.java.getDeclaredMethod("method"),
                                             TestModel.Usage.C1::class.java.getDeclaredMethod("method",
                                                                                              String::class.java),
                                             TestModel.Usage.C2::class.java.getDeclaredMethod("method")),
                                       scanner.methodsWithReturnType(Void.TYPE))
        }

        @Test
        fun `non-primitive`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m3"),
                                             TestModel.C4::class.java.getDeclaredMethod("m4", String::class.java),
                                             TestModel.AC2::class.java.getMethod("value"),
                                             TestModel.AF1::class.java.getMethod("value"),
                                             TestModel.AM1::class.java.getMethod("value")),
                                       scanner.methodsWithReturnType(String::class.java))
        }

        @Test
        fun primitive() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("add",
                                                                                        Int::class.javaPrimitiveType,
                                                                                        Int::class.javaPrimitiveType)),
                                       scanner.methodsWithReturnType(Int::class.javaPrimitiveType!!))
        }
    }

    @Nested
    inner class MethodsWithAnyParamAnnotated {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java).dump()

        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m4", String::class.java)),
                                       scanner.methodsWithAnyParamAnnotated(AM1::class.newAnnotation(Pair("value",
                                                                                                          "2"))))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m4", String::class.java)),
                                       scanner.methodsWithAnyParamAnnotated(TestModel.AM1::class.java))
        }
    }

    @Nested
    inner class ConstructorMatchParams {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java).dump()

        @Test
        fun `no params`() {
            assertToStringEqualsSorted(setOf(TestModel.C1::class.java.getDeclaredConstructor(),
                                             TestModel.C2::class.java.getDeclaredConstructor(),
                                             TestModel.C3::class.java.getDeclaredConstructor(),
                                             TestModel.C4::class.java.getDeclaredConstructor(),
                                             TestModel.C5::class.java.getDeclaredConstructor(),
                                             TestModel.C6::class.java.getDeclaredConstructor(),
                                             TestModel.C7::class.java.getDeclaredConstructor(),
                                             TestModel.Usage.C1::class.java.getDeclaredConstructor(),
                                             TestModel.Usage.C2::class.java.getDeclaredConstructor()),
                                       scanner.constructorsWithParamTypes())
        }

        @Test
        fun `single param`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsWithParamTypes(String::class.java))
        }
    }

    @Nested
    inner class ConstructorsWithAnyParamAnnotated {
        private val scanner = MethodParameterScanner().scan(TestModel::class.java).dump()

        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsWithAnyParamAnnotated(AM1::class.newAnnotation(Pair("value",
                                                                                                               "1"))))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsWithAnyParamAnnotated(TestModel.AM1::class.java))
        }
    }
}