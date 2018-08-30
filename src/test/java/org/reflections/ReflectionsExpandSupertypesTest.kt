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
    fun `expand super types`() {
        val scanners = Scanners(TypeAnnotationsScanner(), SubTypesScanner())
        val conf = scanners.scan(urls = setOf(urlForClass(C::class.java)!!), filter = inputsFilter)
        val subTypes = conf.subTypesOf(A::class.java)
        assertTrue(subTypes.contains(B::class.java), "expanded")
        assertTrue(subTypes.containsAll(conf.subTypesOf(B::class.java)), "transitivity")
    }

    @Test
    fun `do not expand super types`() {
        val scanners = Scanners(TypeAnnotationsScanner(), SubTypesScanner(expandSuperTypes = false))
        val conf = scanners.scan(urls = setOf(urlForClass(C::class.java)!!), filter = inputsFilter)
        val subTypes = conf.subTypesOf(A::class.java)
        assertFalse(subTypes.contains(B::class.java), "expanded")
        assertFalse(subTypes.containsAll(conf.subTypesOf(B::class.java)), "transitivity")
    }
}
