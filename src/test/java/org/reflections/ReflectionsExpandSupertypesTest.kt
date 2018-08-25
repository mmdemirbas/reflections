package org.reflections

import junit.framework.Assert
import org.junit.Test
import org.reflections.Filter.Include
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.A
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.B
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.ScannedScope.C
import org.reflections.util.urlForClass

class ReflectionsExpandSupertypesTest {
    private val inputsFilter = Include(packagePrefix)

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
        val configuration = Configuration()
        configuration.urls = setOf(urlForClass(C::class.java)!!)
        configuration.filter = inputsFilter
        val refExpand = Reflections(configuration)
        Assert.assertTrue(refExpand.configuration.expandSuperTypes)
        val subTypesOf = refExpand.getSubTypesOf(A::class.java)
        Assert.assertTrue("expanded", subTypesOf.contains(B::class.java))
        Assert.assertTrue("transitivity", subTypesOf.containsAll(refExpand.getSubTypesOf(B::class.java)))
    }

    @Test
    fun testNotExpandSupertypes() {
        val configuration = Configuration()
        configuration.urls = setOf(urlForClass(C::class.java)!!)
        configuration.filter = inputsFilter
        configuration.expandSuperTypes = false
        val refDontExpand = Reflections(configuration)
        Assert.assertFalse(refDontExpand.configuration.expandSuperTypes)
        val subTypesOf1 = refDontExpand.getSubTypesOf(A::class.java)
        Assert.assertFalse(subTypesOf1.contains(B::class.java))
    }

    companion object {
        private const val packagePrefix =
                "org.reflections.ReflectionsExpandSupertypesTest\\\$TestModel\\\$ScannedScope\\$.*"
    }
}
