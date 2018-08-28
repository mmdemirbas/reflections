package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.reflections.Filter.Include
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.ResourcesScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.serializers.JsonSerializer
import org.reflections.util.Datum
import org.reflections.util.urlForClass
import java.util.regex.Pattern

class ReflectionsCollectTest : ReflectionsTest() {
    @Test
    override fun testResourcesScanner() {
        val filter = Filter.Composite(listOf(Include(".*\\.xml"), Include(".*\\.json")))
        val reflections =
                Reflections(Configuration(scanners = setOf(ResourcesScanner()),
                                          filter = filter,
                                          urls = setOf(urlForClass(TestModel::class.java)!!)))

        val resolved = reflections.resources(Pattern.compile(".*resource1-reflections\\.xml"))
        assertEquals(setOf(Datum("META-INF/reflections/resource1-reflections.xml")), resolved)

        val resources = reflections.ask<ResourcesScanner, Datum> { keys() }
        assertEquals(setOf(Datum("resource1-reflections.xml"),
                           Datum("resource2-reflections.xml"),
                           Datum("testModel-reflections.xml"),
                           Datum("testModel-reflections.json")), resources)
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val ref1 =
                    Reflections(Configuration(scanners = setOf(SubTypesScanner(false),
                                                               TypeAnnotationsScanner(),
                                                               MethodAnnotationsScanner(),
                                                               MethodParameterNamesScanner(),
                                                               MemberUsageScanner()),
                                              filter = TestModelFilter,
                                              urls = setOf(urlForClass(TestModel::class.java)!!)))

            ref1.save(ReflectionsTest.userDir.resolve("target/test-classes/META-INF/reflections/testModel-reflections.xml"))

            val ref2 =
                    Reflections(Configuration(scanners = setOf(MethodParameterScanner()),
                                              filter = TestModelFilter,
                                              urls = setOf(urlForClass(TestModel::class.java)!!)))

            ref2.save(ReflectionsTest.userDir.resolve("target/test-classes/META-INF/reflections/testModel-reflections.json"),
                      JsonSerializer)

            reflections =
                    Reflections().merged()
                        .merged("META-INF/reflections", Include(".*-reflections.json"), JsonSerializer)
            println(JsonSerializer.toString(reflections))
        }
    }
}
