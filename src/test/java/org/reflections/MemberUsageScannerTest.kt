package org.reflections

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MemberUsageScannerTest {
    @Nested
    inner class Usages {
        private val scanner = MemberUsageScanner().scan(TestModel::class.java).dump()

        @Test
        fun `ctor - with param`() {
            assertToStringEqualsSorted(setOf(TestModel.Usage.C2::class.java.getDeclaredMethod(
                    "method")),
                                                       scanner.usages(TestModel.Usage.C1::class.java.getDeclaredConstructor(
                                                               TestModel.Usage.C2::class.java)))
        }

        @Test
        fun `ctor - no param`() {
            assertToStringEqualsSorted(setOf(TestModel.Usage.C2::class.java.getDeclaredConstructor(),
                                             TestModel.Usage.C2::class.java.getDeclaredMethod(
                                                                     "method")),
                                                       scanner.usages(TestModel.Usage.C1::class.java.getDeclaredConstructor()))
        }

        @Test
        fun `method - with param`() {
            assertToStringEqualsSorted(setOf(TestModel.Usage.C2::class.java.getDeclaredMethod(
                    "method")),
                                                       scanner.usages(TestModel.Usage.C1::class.java.getDeclaredMethod(
                                                               "method",
                                                               String::class.java)))
        }

        @Test
        fun `method - no param`() {
            assertToStringEqualsSorted(setOf(TestModel.Usage.C2::class.java.getDeclaredMethod(
                    "method")),
                                                       scanner.usages(TestModel.Usage.C1::class.java.getDeclaredMethod(
                                                               "method")))
        }

        @Test
        fun field() {
            assertToStringEqualsSorted(setOf(TestModel.Usage.C1::class.java.getDeclaredConstructor(),
                                             TestModel.Usage.C1::class.java.getDeclaredConstructor(
                                                                     TestModel.Usage.C2::class.java),
                                             TestModel.Usage.C1::class.java.getDeclaredMethod(
                                                                     "method"),
                                             TestModel.Usage.C1::class.java.getDeclaredMethod(
                                                                     "method",
                                                                     String::class.java)),
                                                       scanner.usages(TestModel.Usage.C1::class.java.getDeclaredField(
                                                               "c2")).toSet())
        }
    }
}