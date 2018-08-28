package org.reflections

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.reflections.Filter.Include
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.A
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.B
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.ScannedScope.C
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.urlForClass

class ReflectionsExpandSupertypesTest {
    private val inputsFilter =
            Include("org.reflections.ReflectionsExpandSupertypesTest\\\$TestModel\\\$ScannedScope\\$.*")

    interface TestModel {
        interface A // outside of scanned scope

        interface B : A // outside of scanned scope, but immediate supertype

        interface ScannedScope {
            interface C : B

            interface D : B
        }
    }

    @Test
    fun testExpandSupertypes() {
        val refExpand =
                Configuration(scanners = setOf(TypeAnnotationsScanner(), SubTypesScanner()),
                              urls = setOf(urlForClass(C::class.java)!!),
                              filter = inputsFilter).withScan()
        val subTypes = refExpand.subTypesOf(A::class.java)
        assertTrue(subTypes.contains(B::class.java), "expanded")
        assertTrue(subTypes.containsAll(refExpand.subTypesOf(B::class.java)), "transitivity")
    }

    @Test
    fun testNotExpandSupertypes() {
        val refDontExpand =
                Configuration(scanners = setOf(TypeAnnotationsScanner(), SubTypesScanner(expandSuperTypes = false)),
                              urls = setOf(urlForClass(C::class.java)!!),
                              filter = inputsFilter).withScan()
        val subTypesOf1 = refDontExpand.subTypesOf(A::class.java)
        assertFalse(subTypesOf1.contains(B::class.java))
    }
}
