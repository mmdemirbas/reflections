package org.reflections

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.reflections.SubTypesScannerTest.ScannedScope.C

class SubTypesScannerTest {
    @Nested
    inner class AllTypes {
        @Test
        fun `object included`() {
            Assertions.assertTrue(SubTypesScanner(excludeObjectClass = false).scan(TestModel::class.java).dump().allTypes().isNotEmpty())
        }

        @Test
        fun `object excluded`() {
            assertThrows<RuntimeException> {
                SubTypesScanner(excludeObjectClass = true).scan(TestModel::class.java).dump().allTypes()
            }
        }
    }

    @Nested
    inner class SubTypesOf {
        private val scanner = SubTypesScanner(excludeObjectClass = false).scan(TestModel::class.java).dump()

        @Test
        fun `interface`() {
            assertToStringEqualsSorted(setOf(TestModel.I2::class.java,
                                             TestModel.C1::class.java,
                                             TestModel.C2::class.java,
                                             TestModel.C3::class.java,
                                             TestModel.C5::class.java), scanner.subTypesOf(TestModel.I1::class.java))
        }

        @Test
        fun `class`() {
            assertToStringEqualsSorted(setOf(TestModel.C2::class.java,
                                             TestModel.C3::class.java,
                                             TestModel.C5::class.java), scanner.subTypesOf(TestModel.C1::class.java))
        }
    }

    interface A // outside of scanned scope

    interface B : A // outside of scanned scope, but immediate supertype

    interface ScannedScope {
        interface C : B

        interface D : B
    }

    @Nested
    inner class ExpandSuperTypes {
        private val inputsFilter = Filter.Include("org.reflections.ExpandSuperTypes\\\$TestModel\\\$ScannedScope\\$.*")

        @Test
        fun `expand super types`() {
            val scanners = SubTypesScanner()
            val conf = scanners.scan(urls = setOf(urlForClass(C::class.java)!!), filter = inputsFilter)
            val subTypes = conf.subTypesOf(A::class.java)
            Assertions.assertTrue(subTypes.contains(B::class.java), "expanded")
            Assertions.assertTrue(subTypes.containsAll(conf.subTypesOf(B::class.java)), "transitivity")
        }

        @Test
        fun `do not expand super types`() {
            val scanners = SubTypesScanner(expandSuperTypes = false)
            val conf = scanners.scan(urls = setOf(urlForClass(C::class.java)!!), filter = inputsFilter)
            val subTypes = conf.subTypesOf(A::class.java)
            Assertions.assertFalse(subTypes.contains(B::class.java), "expanded")
            Assertions.assertFalse(subTypes.containsAll(conf.subTypesOf(B::class.java)), "transitivity")
        }
    }
}