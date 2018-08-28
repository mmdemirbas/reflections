package org.reflections

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.reflections.Filter.Exclude
import org.reflections.Filter.Include

/**
 * Test filtering
 */
class FilterTest {
    @Test
    fun test_include() {
        val filter = Include("org\\.reflections.*")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }

    @Test
    fun test_includePackage() {
        val filter = Include("org.reflections".toPrefixRegex())
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }

    @Test
    fun test_includePackageMultiple() {
        val filter = Filter.Composite(listOf("org.reflections", "org.foo").map { prefix ->
            Include(prefix.toPrefixRegex())
        })
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foo.Reflections"))
        assertTrue(filter.test("org.foo.bar.Reflections"))
        assertFalse(filter.test("org.bar.Reflections"))
    }

    @Test
    fun test_includePackagebyClass() {
        val filter = Include(Reflections::class.java.toPackageNameRegex())
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_exclude() {
        val filter = Exclude("org\\.reflections.*")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
    }

    @Test
    fun test_excludePackage() {
        val filter = Exclude("org.reflections".toPrefixRegex())
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
    }

    @Test
    fun test_excludePackageByClass() {
        val filter = Exclude(Reflections::class.java.toPackageNameRegex())
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_parse_include() {
        val filter = Filter.parse("+org.reflections.*")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
        assertTrue(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parse_include_notRegex() {
        val filter = Filter.parse("+org.reflections")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
        assertFalse(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parse_exclude() {
        val filter = Filter.parse("-org.reflections.*")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
        assertFalse(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parse_exclude_notRegex() {
        val filter = Filter.parse("-org.reflections")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
        assertTrue(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parse_include_exclude() {
        val filter = Filter.parse("+org.reflections.*, -org.reflections.foo.*")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_parsePackages_include() {
        val filter = Filter.parsePackages("+org.reflections")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
        assertFalse(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parsePackages_include_trailingDot() {
        val filter = Filter.parsePackages("+org.reflections.")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertTrue(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
        assertFalse(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parsePackages_exclude() {
        val filter = Filter.parsePackages("-org.reflections")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
        assertTrue(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parsePackages_exclude_trailingDot() {
        val filter = Filter.parsePackages("-org.reflections.")
        assertFalse(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertTrue(filter.test("org.foobar.Reflections"))
        assertTrue(filter.test("org.reflectionsplus.Reflections"))
    }

    @Test
    fun test_parsePackages_include_exclude() {
        val filter = Filter.parsePackages("+org.reflections, -org.reflections.foo")
        assertTrue(filter.test("org.reflections.Reflections"))
        assertFalse(filter.test("org.reflections.foo.Reflections"))
        assertFalse(filter.test("org.foobar.Reflections"))
    }
}
