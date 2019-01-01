package com.mmdemirbas.reflections

import org.junit.jupiter.api.Test

class MethodReturnTypesScannerTest {
    private val scanner = MethodReturnTypesScanner().scan(TestModel::class.java).dump()

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
                                         TestModel.Usage.C1::class.java.getDeclaredMethod("method", String::class.java),
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