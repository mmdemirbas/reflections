package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.reflections.TestModel.C3
import org.reflections.TestModel.C4

/**
 * @author mamo
 */
class TestUtilsTests {
    @Test
    fun allMethods() {
        assertEquals(listOf("add", "c2toC3", "m1", "m1", "m1", "m3", "m4"),
                     C4::class.java.allMethods().map { it.name }.sorted())
    }

    @Test
    fun allFields() {
        assertEquals(listOf("f1", "f2", "f3"), C4::class.java.allFields().map { it.name })
    }

    @Test
    fun allAnnotations() {
        assertEquals(listOf(TestModel.AC2::class.newAnnotation("value" to "ugh?!"),
                            TestModel.AC1::class.newAnnotation(),
                            TestModel.AC1n::class.newAnnotation(),
                            TestModel.AI2::class.newAnnotation(),
                            TestModel.AI1::class.newAnnotation()), C3::class.java.allAnnotations())
    }
}
