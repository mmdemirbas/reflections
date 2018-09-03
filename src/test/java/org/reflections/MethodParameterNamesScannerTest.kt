package org.reflections

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MethodParameterNamesScannerTest {
    @Nested
    inner class ParamNames {
        private val scanner = MethodParameterNamesScanner().scan(TestModel::class.java).dump()

        @Test
        fun ctor() {
            assertToStringEqualsSorted(listOf("f1"),
                                       scanner.paramNames(TestModel.C4::class.java.getDeclaredConstructor(String::class.java)))
        }

        @Test
        fun `multiple params`() {
            assertToStringEqualsSorted(listOf("i1", "i2"),
                                       scanner.paramNames(TestModel.C4::class.java.getDeclaredMethod("add",
                                                                                                     Int::class.javaPrimitiveType,
                                                                                                     Int::class.javaPrimitiveType)))
        }

        @Test
        fun `single param`() {
            assertToStringEqualsSorted(listOf("string"),
                                       scanner.paramNames(TestModel.C4::class.java.getDeclaredMethod("m4",
                                                                                                     String::class.java)))
        }

        @Test
        fun `no param`() {
            assertToStringEqualsSorted(emptyList<Any>(),
                                       scanner.paramNames(TestModel.C4::class.java.getDeclaredMethod("m3")))
        }
    }
}