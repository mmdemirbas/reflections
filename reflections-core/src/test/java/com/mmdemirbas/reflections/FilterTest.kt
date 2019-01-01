package com.mmdemirbas.reflections

import com.mmdemirbas.reflections.Filter.Exclude
import com.mmdemirbas.reflections.Filter.Include
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Test filtering
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilterTest {
    // todo: bu testleri de düzenle, bölünebilir, bir kısmı silinebilir

    @Test
    fun test_include() {
        val filter = Include("org\\.reflections.*")
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertTrue(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertFalse(filter.acceptFqn("org.foobar.Configuration"))
    }

    @Test
    fun test_includePackage() {
        val filter = Include("org.reflections".toPrefixRegex())
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertTrue(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertFalse(filter.acceptFqn("org.foobar.Configuration"))
    }

    @Test
    fun test_includePackageMultiple() {
        val filter = Filter.Composite(listOf("org.reflections", "org.foo").map { prefix ->
            Include(prefix.toPrefixRegex())
        })
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertTrue(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertTrue(filter.acceptFqn("org.foo.Configuration"))
        assertTrue(filter.acceptFqn("org.foo.bar.Configuration"))
        assertFalse(filter.acceptFqn("org.bar.Configuration"))
    }

    @Test
    fun test_includePackagebyClass() {
        val filter = Include(Scanner::class.java.toPackageNameRegex())
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertTrue(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertFalse(filter.acceptFqn("org.foobar.Configuration"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_exclude() {
        val filter = Exclude("org\\.reflections.*")
        assertFalse(filter.acceptFqn("org.reflections.Configuration"))
        assertFalse(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertTrue(filter.acceptFqn("org.foobar.Configuration"))
    }

    @Test
    fun test_excludePackage() {
        val filter = Exclude("org.reflections".toPrefixRegex())
        assertFalse(filter.acceptFqn("org.reflections.Configuration"))
        assertFalse(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertTrue(filter.acceptFqn("org.foobar.Configuration"))
    }

    @Test
    fun test_excludePackageByClass() {
        val filter = Exclude(Scanner::class.java.toPackageNameRegex())
        assertFalse(filter.acceptFqn("org.reflections.Configuration"))
        assertFalse(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertTrue(filter.acceptFqn("org.foobar.Configuration"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_parse_include() {
        val filter = parseFilter("+org.reflections.*")
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertTrue(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertFalse(filter.acceptFqn("org.foobar.Configuration"))
        assertTrue(filter.acceptFqn("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parse_include_notRegex() {
        val filter = parseFilter("+org.reflections")
        assertFalse(filter.acceptFqn("org.reflections.Configuration"))
        assertFalse(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertFalse(filter.acceptFqn("org.foobar.Configuration"))
        assertFalse(filter.acceptFqn("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parse_exclude() {
        val filter = parseFilter("-org.reflections.*")
        assertFalse(filter.acceptFqn("org.reflections.Configuration"))
        assertFalse(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertTrue(filter.acceptFqn("org.foobar.Configuration"))
        assertFalse(filter.acceptFqn("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parse_exclude_notRegex() {
        val filter = parseFilter("-org.reflections")
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertTrue(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertTrue(filter.acceptFqn("org.foobar.Configuration"))
        assertTrue(filter.acceptFqn("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parse_include_exclude() {
        val filter = parseFilter("+org.reflections.*, -org.reflections.foo.*")
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertFalse(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertFalse(filter.acceptFqn("org.foobar.Configuration"))
    }

    //-----------------------------------------------------------------------
    @Test
    fun test_parsePackages_include() {
        val filter = parsePackagesFilter("+org.reflections")
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertTrue(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertFalse(filter.acceptFqn("org.foobar.Configuration"))
        assertFalse(filter.acceptFqn("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parsePackages_include_trailingDot() {
        val filter = parsePackagesFilter("+org.reflections.")
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertTrue(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertFalse(filter.acceptFqn("org.foobar.Configuration"))
        assertFalse(filter.acceptFqn("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parsePackages_exclude() {
        val filter = parsePackagesFilter("-org.reflections")
        assertFalse(filter.acceptFqn("org.reflections.Configuration"))
        assertFalse(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertTrue(filter.acceptFqn("org.foobar.Configuration"))
        assertTrue(filter.acceptFqn("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parsePackages_exclude_trailingDot() {
        val filter = parsePackagesFilter("-org.reflections.")
        assertFalse(filter.acceptFqn("org.reflections.Configuration"))
        assertFalse(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertTrue(filter.acceptFqn("org.foobar.Configuration"))
        assertTrue(filter.acceptFqn("org.reflectionsplus.Configuration"))
    }

    @Test
    fun test_parsePackages_include_exclude() {
        val filter = parsePackagesFilter("+org.reflections, -org.reflections.foo")
        assertTrue(filter.acceptFqn("org.reflections.Configuration"))
        assertFalse(filter.acceptFqn("org.reflections.foo.Configuration"))
        assertFalse(filter.acceptFqn("org.foobar.Configuration"))
    }

    @ParameterizedTest
    @MethodSource
    fun Pair<String, String>.fqnToResourceName() {
        assertEquals(second, first.fqnToResourceName())
    }

    fun fqnToResourceName() = listOf("" to "", "a.b.c" to "a/b/c.class", "a.b.c\$d.e.f" to "a/b/c.class")
}
