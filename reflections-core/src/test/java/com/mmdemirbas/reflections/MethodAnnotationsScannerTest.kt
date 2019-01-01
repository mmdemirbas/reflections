package com.mmdemirbas.reflections

import com.mmdemirbas.reflections.TestModel.AM1
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MethodAnnotationsScannerTest {
    @Nested
    inner class MethodsAnnotatedWith {
        private val scanner = MethodAnnotationsScanner().scan(TestModel::class.java).dump()

        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m1"),
                                             TestModel.C4::class.java.getDeclaredMethod("m1",
                                                                                        Int::class.javaPrimitiveType,
                                                                                        Array<String>::class.java),
                                             TestModel.C4::class.java.getDeclaredMethod("m1",
                                                                                        Array<IntArray>::class.java,
                                                                                        Array<Array<String>>::class.java)),
                                       scanner.methodsAnnotatedWith(AM1::class.newAnnotation(Pair("value", "1"))))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredMethod("m1"),
                                             TestModel.C4::class.java.getDeclaredMethod("m1",
                                                                                        Int::class.javaPrimitiveType,
                                                                                        Array<String>::class.java),
                                             TestModel.C4::class.java.getDeclaredMethod("m1",
                                                                                        Array<IntArray>::class.java,
                                                                                        Array<Array<String>>::class.java),
                                             TestModel.C4::class.java.getDeclaredMethod("m3")),
                                       scanner.methodsAnnotatedWith(TestModel.AM1::class.java))
        }
    }

    @Nested
    inner class ConstructorsAnnotatedWith {
        private val scanner = MethodAnnotationsScanner().scan(TestModel::class.java).dump()

        @Test
        fun `by instance`() {
            val am1 = AM1::class.newAnnotation(Pair("value", "1"))
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsAnnotatedWith(am1))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredConstructor(String::class.java)),
                                       scanner.constructorsAnnotatedWith(TestModel.AM1::class.java))
        }
    }
}