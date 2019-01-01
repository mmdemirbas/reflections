package com.mmdemirbas.reflections

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

class ResourceScannerTest {
    @Nested
    inner class Resources {
        @Test
        fun resources1() {
            val scanner =
                    ResourceScanner().scan(urls = setOf(ScanCommand.ScanClass(TestModel::class.java).toUrl()),
                                           filter = Filter.Composite(listOf(Filter.Include(".*\\.xml"),
                                                                            Filter.Exclude(".*testModel-reflections\\.xml"))))
                        .dump()

            assertToStringEqualsSorted(setOf("META-INF/reflections/resource1-reflections.xml"),
                                       scanner.resources(Pattern.compile(".*resource1-reflections\\.xml")))

            assertToStringEqualsSorted(setOf("resource1-reflections.xml", "resource2-reflections.xml"),
                                       scanner.resources())
        }


        @Test
        fun resources2() {
            val scanner =
                    ResourceScanner().scan(urls = setOf(ScanCommand.ScanClass(TestModel::class.java).toUrl()),
                                           filter = Filter.Composite(listOf(Filter.Include(".*\\.xml"),
                                                                            Filter.Include(".*\\.json")))).dump()

            assertToStringEqualsSorted(setOf("META-INF/reflections/resource1-reflections.xml"),
                                       scanner.resources(Pattern.compile(".*resource1-reflections\\.xml")))

            assertToStringEqualsSorted(setOf("resource1-reflections.xml",
                                             "resource2-reflections.xml",
                                             "testModel-reflections.xml",
                                             "testModel-reflections.json"), scanner.resources())
        }
    }
}