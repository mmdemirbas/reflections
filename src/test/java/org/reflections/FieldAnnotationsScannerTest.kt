package org.reflections

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FieldAnnotationsScannerTest {
    @Nested
    inner class FieldsAnnotatedWith {
        private val scanner = FieldAnnotationsScanner()
            .scan(TestModel::class.java).dump()

        @Test
        fun `by instance`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredField("f2")),
                                       scanner.fieldsAnnotatedWith(JavaSpecific.newAF1("2")))
        }

        @Test
        fun `by type`() {
            assertToStringEqualsSorted(setOf(TestModel.C4::class.java.getDeclaredField("f1"),
                                             TestModel.C4::class.java.getDeclaredField("f2")),
                                       scanner.fieldsAnnotatedWith(TestModel.AF1::class.java))
        }
    }
}