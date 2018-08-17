package org.reflections

import junit.framework.Assert
import org.junit.Test
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.A
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.B
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.ScannedScope.C
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder

class ReflectionsExpandSupertypesTest {
    private val inputsFilter = FilterBuilder().include(packagePrefix)

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
                Reflections(ConfigurationBuilder().setUrls(ClasspathHelper.forClass(C::class.java)!!).filterInputsBy(
                        inputsFilter.asPredicate()))
        Assert.assertTrue(refExpand.configuration.shouldExpandSuperTypes())
        val subTypesOf = refExpand.getSubTypesOf(A::class.java)
        Assert.assertTrue("expanded", subTypesOf.contains(B::class.java))
        Assert.assertTrue("transitivity", subTypesOf.containsAll(refExpand.getSubTypesOf(B::class.java)))
    }

    @Test
    fun testNotExpandSupertypes() {
        val refDontExpand =
                Reflections(ConfigurationBuilder().setUrls(ClasspathHelper.forClass(C::class.java)!!).filterInputsBy(
                        inputsFilter.asPredicate()).setExpandSuperTypes(false))
        Assert.assertFalse(refDontExpand.configuration.shouldExpandSuperTypes())
        val subTypesOf1 = refDontExpand.getSubTypesOf(A::class.java)
        Assert.assertFalse(subTypesOf1.contains(B::class.java))
    }

    companion object {

        private val packagePrefix = "org.reflections.ReflectionsExpandSupertypesTest\\\$TestModel\\\$ScannedScope\\$.*"
    }
}
