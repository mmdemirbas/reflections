package org.reflections

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

class ResourceScannerTest {
    @Nested
    inner class Resources {
        @Test
        fun resources1() {
            val scanner =
                    ResourceScanner().scan(filter = Filter.Composite(listOf(Filter.Include(".*\\.xml"),
                                                                            Filter.Exclude(".*testModel-reflections\\.xml"))),
                                           urls = setOf(urlForClass(TestModel::class.java)!!)).dump()

            assertToStringEqualsSorted(setOf("META-INF/reflections/resource1-reflections.xml"),
                                       scanner.resources(Pattern.compile(".*resource1-reflections\\.xml")))

            assertToStringEqualsSorted(setOf("resource1-reflections.xml", "resource2-reflections.xml"),
                                       scanner.resources())
        }


        @Test
        fun resources2() {
            val scanner =
                    ResourceScanner().scan(filter = Filter.Composite(listOf(Filter.Include(".*\\.xml"),
                                                                            Filter.Include(".*\\.json"))),
                                           urls = setOf(urlForClass(TestModel::class.java)!!)).dump()

            assertToStringEqualsSorted(setOf("META-INF/reflections/resource1-reflections.xml"),
                                       scanner.resources(Pattern.compile(".*resource1-reflections\\.xml")))

            assertToStringEqualsSorted(setOf("resource1-reflections.xml",
                                             "resource2-reflections.xml",
                                             "testModel-reflections.xml",
                                             "testModel-reflections.json"), scanner.resources())
        }
    }
}