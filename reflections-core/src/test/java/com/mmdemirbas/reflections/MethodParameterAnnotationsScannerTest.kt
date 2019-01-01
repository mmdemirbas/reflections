package com.mmdemirbas.reflections

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MethodParameterAnnotationsScannerTest {
    private val scanner = MethodParameterAnnotationsScanner().scan(TestModel::class.java).dump()

    @Nested
    inner class methodsWithAnyParamAnnotated {
        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m4", String::class.java)),
                                       scanner.methodsWithAnyParamAnnotated(TestModel.AM1::class.newAnnotation(Pair("value",
                                                                                                                    "2"))))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m4", String::class.java)),
                                       scanner.methodsWithAnyParamAnnotated(TestModel.AM1::class.java))
        }
    }

    @Nested
    inner class ConstructorsWithAnyParamAnnotated {
        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsWithAnyParamAnnotated(TestModel.AM1::class.newAnnotation(Pair(
                                               "value",
                                               "1"))))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsWithAnyParamAnnotated(TestModel.AM1::class.java))
        }
    }
}