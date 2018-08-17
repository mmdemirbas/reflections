package org.reflections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.reflections.util.FilterBuilder

/**
 * Test filtering
 */
class FilterBuilderTest {

    @Test
    fun test_include() {
        val filter = FilterBuilder().include("org\\.reflections.*")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }

    @Test
    fun test_includePackage() {
        val filter = FilterBuilder().includePackage("org.reflections")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }

    @Test
    fun test_includePackageMultiple() {
        val filter = FilterBuilder().includePackage("org.reflections", "org.foo")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foo.Reflections"))
        assertTrue(filter.test("org.foo.bar.Reflections"))
        assertFalse(filter.test("org.bar.Reflections"))
    }

    @Test
    fun test_includePackagebyClass() {
        val filter = FilterBuilder().includePackage(Reflections::class.java)
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_exclude() {
        val filter = FilterBuilder().exclude("org\\.reflections.*")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
    }

    @Test
    fun test_excludePackage() {
        val filter = FilterBuilder().excludePackage("org.reflections")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
    }

    @Test
    fun test_excludePackageByClass() {
        val filter = FilterBuilder().excludePackage(Reflections::class.java)
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_parse_include() {
        val filter = FilterBuilder.parse("+org.reflections.*")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
        assertTrue(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parse_include_notRegex() {
        val filter = FilterBuilder.parse("+org.reflections")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
        assertFalse(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parse_exclude() {
        val filter = FilterBuilder.parse("-org.reflections.*")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
        assertFalse(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parse_exclude_notRegex() {
        val filter = FilterBuilder.parse("-org.reflections")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
        assertTrue(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parse_include_exclude() {
        val filter = FilterBuilder.parse("+org.reflections.*, -org.reflections.foo.*")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_parsePackages_include() {
        val filter = FilterBuilder.parsePackages("+org.reflections")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
        assertFalse(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parsePackages_include_trailingDot() {
        val filter = FilterBuilder.parsePackages("+org.reflections.")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
        assertFalse(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parsePackages_exclude() {
        val filter = FilterBuilder.parsePackages("-org.reflections")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
        assertTrue(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parsePackages_exclude_trailingDot() {
        val filter = FilterBuilder.parsePackages("-org.reflections.")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
        assertTrue(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parsePackages_include_exclude() {
        val filter = FilterBuilder.parsePackages("+org.reflections, -org.reflections.foo")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }

}
