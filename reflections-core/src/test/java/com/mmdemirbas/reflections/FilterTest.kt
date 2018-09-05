package com.mmdemirbas.reflections

import com.mmdemirbas.reflections.Filter.Exclude
import com.mmdemirbas.reflections.Filter.Include
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test filtering
 */
class FilterTest {
    // todo: bu testleri de düzenle, bölünebilir, bir kısmı silinebilir

    @Test
    fun test_include() {
        val filter = Include("org\\.reflections.*")
        assertTrue(filter.test("org.reflections.Configuration"))
        assertTrue(filter.test("org.reflections.foo.Configuration"))
        assertFalse(filter.test("org.foobar.Configuration"))
    }

    @Test
    fun test_includePackage() {
        val filter = Include("org.reflections".toPrefixRegex())
        assertTrue(filter.test("org.reflections.Configuration"))
        assertTrue(filter.test("org.reflections.foo.Configuration"))
        assertFalse(filter.test("org.foobar.Configuration"))
    }

    @Test
    fun test_includePackageMultiple() {
        val filter = Filter.Composite(listOf("org.reflections", "org.foo").map { prefix ->
            Include(prefix.toPrefixRegex())
        })
        assertTrue(filter.test("org.reflections.Configuration"))
        assertTrue(filter.test("org.reflections.foo.Configuration"))
        assertTrue(filter.test("org.foo.Configuration"))
        assertTrue(filter.test("org.foo.bar.Configuration"))
        assertFalse(filter.test("org.bar.Configuration"))
    }

    @Test
    fun test_includePackagebyClass() {
        val filter = Include(Scanner::class.java.toPackageNameRegex())
        assertTrue(filter.test("org.reflections.Configuration"))
        assertTrue(filter.test("org.reflections.foo.Configuration"))
        assertFalse(filter.test("org.foobar.Configuration"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_exclude() {
        val filter = Exclude("org\\.reflections.*")
        assertFalse(filter.test("org.reflections.Configuration"))
        assertFalse(filter.test("org.reflections.foo.Configuration"))
        assertTrue(filter.test("org.foobar.Configuration"))
    }

    @Test
    fun test_excludePackage() {
        val filter = Exclude("org.reflections".toPrefixRegex())
        assertFalse(filter.test("org.reflections.Configuration"))
        assertFalse(filter.test("org.reflections.foo.Configuration"))
        assertTrue(filter.test("org.foobar.Configuration"))
    }

    @Test
    fun test_excludePackageByClass() {
        val filter = Exclude(Scanner::class.java.toPackageNameRegex())
        assertFalse(filter.test("org.reflections.Configuration"))
        assertFalse(filter.test("org.reflections.foo.Configuration"))
        assertTrue(filter.test("org.foobar.Configuration"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_parse_include() {
        val filter = parseFilter("+org.reflections.*")
        assertTrue(filter.test("org.reflections.Configuration"))
        assertTrue(filter.test("org.reflections.foo.Configuration"))
        assertFalse(filter.test("org.foobar.Configuration"))
        assertTrue(filter.test("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parse_include_notRegex() {
        val filter = parseFilter("+org.reflections")
        assertFalse(filter.test("org.reflections.Configuration"))
        assertFalse(filter.test("org.reflections.foo.Configuration"))
        assertFalse(filter.test("org.foobar.Configuration"))
        assertFalse(filter.test("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parse_exclude() {
        val filter = parseFilter("-org.reflections.*")
        assertFalse(filter.test("org.reflections.Configuration"))
        assertFalse(filter.test("org.reflections.foo.Configuration"))
        assertTrue(filter.test("org.foobar.Configuration"))
        assertFalse(filter.test("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parse_exclude_notRegex() {
        val filter = parseFilter("-org.reflections")
        assertTrue(filter.test("org.reflections.Configuration"))
        assertTrue(filter.test("org.reflections.foo.Configuration"))
        assertTrue(filter.test("org.foobar.Configuration"))
        assertTrue(filter.test("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parse_include_exclude() {
        val filter = parseFilter("+org.reflections.*, -org.reflections.foo.*")
        assertTrue(filter.test("org.reflections.Configuration"))
        assertFalse(filter.test("org.reflections.foo.Configuration"))
        assertFalse(filter.test("org.foobar.Configuration"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_parsePackages_include() {
        val filter = parsePackagesFilter("+org.reflections")
        assertTrue(filter.test("org.reflections.Configuration"))
        assertTrue(filter.test("org.reflections.foo.Configuration"))
        assertFalse(filter.test("org.foobar.Configuration"))
        assertFalse(filter.test("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parsePackages_include_trailingDot() {
        val filter = parsePackagesFilter("+org.reflections.")
        assertTrue(filter.test("org.reflections.Configuration"))
        assertTrue(filter.test("org.reflections.foo.Configuration"))
        assertFalse(filter.test("org.foobar.Configuration"))
        assertFalse(filter.test("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parsePackages_exclude() {
        val filter = parsePackagesFilter("-org.reflections")
        assertFalse(filter.test("org.reflections.Configuration"))
        assertFalse(filter.test("org.reflections.foo.Configuration"))
        assertTrue(filter.test("org.foobar.Configuration"))
        assertTrue(filter.test("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parsePackages_exclude_trailingDot() {
        val filter = parsePackagesFilter("-org.reflections.")
        assertFalse(filter.test("org.reflections.Configuration"))
        assertFalse(filter.test("org.reflections.foo.Configuration"))
        assertTrue(filter.test("org.foobar.Configuration"))
        assertTrue(filter.test("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parsePackages_include_exclude() {
        val filter = parsePackagesFilter("+org.reflections, -org.reflections.foo")
        assertTrue(filter.test("org.reflections.Configuration"))
        assertFalse(filter.test("org.reflections.foo.Configuration"))
        assertFalse(filter.test("org.foobar.Configuration"))
    }
}
