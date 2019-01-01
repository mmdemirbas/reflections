package com.mmdemirbas.reflections

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MethodParameterTypesScannerTest {
    private val scanner = MethodParameterTypesScanner().scan(TestModel::class.java).dump()

    @Nested
    inner class MethodsWithParamTypes {
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
    inner class constructorsWithParamTypes {
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
}