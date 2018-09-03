package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

/**
 * @author Muhammed Demirbaş
 * @since 2018-09-02 15:18
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NewAnnotationTest {
    @Target(AnnotationTarget.CLASS)
    annotation class MyAnno(val value: String,
                            val int: Int = 1,
                            val long: Long = 2L,
                            val short: Short = 3,
                            val double: Double = 0.4,
                            val float: Float = 0.5f,
                            val boolean: Boolean = true,
                            val char: Char = 'M',
                            val stringArray: Array<String> = arrayOf("a comment"))

    @MyAnno("same")
    class SameWithDefaults

    @MyAnno("same",
            int = 1,
            long = 2L,
            short = 3,
            double = 0.4,
            float = 0.5f,
            boolean = true,
            char = 'M',
            stringArray = arrayOf("a comment"))
    class SameWithExplicits

    @MyAnno("same", int = 999)
    class Different

    private val self = MyAnno::class.newAnnotation("value" to "same")
    private val other = SameWithDefaults::class.java.getDeclaredAnnotation(MyAnno::class.java)!!
    private val another = SameWithExplicits::class.java.getDeclaredAnnotation(MyAnno::class.java)!!
    private val different = Different::class.java.getDeclaredAnnotation(MyAnno::class.java)!!

    @Nested
    inner class `Missing property check` {
        @Test
        fun `fail early if a required property missing`() {
            assertThrows<RuntimeException> { MyAnno::class.newAnnotation() }
        }

        @Test
        fun `do not fail if an optional property missing`() {
            assertNotNull(MyAnno::class.newAnnotation("value" to "same"))
        }
    }

    @Nested
    inner class Equals {
        @Test
        fun reflexive() {
            assertTrue(self.equals(self))
        }

        @Disabled("Couldn't make symmetric :(")
        @Test
        fun symmetric() {
            assumeTrue(self.equals(other))
            assertTrue(other.equals(self))
        }

        @Test
        fun transitive() {
            assumeTrue(self.equals(other))
            assumeTrue(other.equals(another))
            assertTrue(self.equals(another))
        }

        @Test
        fun consistent() {
            assumeTrue(self.equals(other))
            for (i in 1..1000) assertTrue(self.equals(other), "at pass $i")
        }

        @Test
        fun `null`() {
            assertFalse(self.equals(null))
        }

        @Test
        fun `different type`() {
            assertFalse(self.equals(1))
        }

        @Test
        fun `different value`() {
            assertFalse(self.equals(different))
        }

        @Test
        fun `same value`() {
            assertTrue(self.equals(other))
        }
    }

    @Nested
    inner class HashCode {
        @Test
        fun `hashCode compatible with the contract`() {
            assertEquals(other.hashCode(), self.hashCode())
        }
    }

    @Nested
    inner class ToString {
        @Test
        fun `toString compatible with the contract`() {
            // Field order of the original Annotation.toString() doesn't make sense:
            // assertEquals(other.toString(), self.toString())

            // So, we couldn't mimic it and need to define expected value explicitly:
            assertEquals("@org.reflections.NewAnnotationTest\$MyAnno(value=same, boolean=true, char=M, short=3, int=1, long=2, float=0.5, double=0.4, stringArray=[a comment])",
                         self.toString())
        }
    }

    @Test
    fun `property can be set`() {
        assertEquals(other.value, self.value)
    }

    @Test
    fun `annotationType returns the actual annotation class`() {
        assertEquals(other.annotationType(), self.annotationType())
    }
}