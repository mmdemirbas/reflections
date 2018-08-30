package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReflectionsCollectTest : ReflectionsTest() {
    @BeforeAll
    override fun setup() {
        val conf1 =
                Configuration(scanners = setOf(SubTypesScanner(false),
                                               TypeAnnotationsScanner(),
                                               MethodAnnotationsScanner(),
                                               MethodParameterNamesScanner(),
                                               MemberUsageScanner()),
                              filter = TestModelFilter,
                              urls = setOf(urlForClass(TestModel::class.java)!!)).withScan()

        conf1.save(userDir.resolve("target/test-classes/META-INF/reflections/testModel-reflections.xml"))

        val conf2 =
                Configuration(scanners = setOf(MethodParameterScanner()),
                              filter = TestModelFilter,
                              urls = setOf(urlForClass(TestModel::class.java)!!)).withScan()

        conf2.save(userDir.resolve("target/test-classes/META-INF/reflections/testModel-reflections.json"),
                   JsonSerializer)

        configuration =
                merged(merged(Configuration()), "META-INF/reflections", Include(".*-reflections.json"), JsonSerializer)
        println(JsonSerializer.toString(configuration))
    }

    @Test
    override fun resources() {
        val filter = Filter.Composite(listOf(Include(".*\\.xml"), Include(".*\\.json")))
        val configuration =
                Configuration(scanners = setOf(ResourcesScanner()),
                              filter = filter,
                              urls = setOf(urlForClass(TestModel::class.java)!!)).withScan()

        val resolved = configuration.resources(Pattern.compile(".*resource1-reflections\\.xml"))
        assertEquals(setOf(Datum("META-INF/reflections/resource1-reflections.xml")), resolved)

        val resources = configuration.ask<ResourcesScanner, Datum> { keys() }
        assertEquals(setOf(Datum("resource1-reflections.xml"),
                           Datum("resource2-reflections.xml"),
                           Datum("testModel-reflections.xml"),
                           Datum("testModel-reflections.json")), resources)
    }
}
