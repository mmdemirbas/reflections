package org.reflections

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
class ReflectionsCollectTest : ReflectionsTestBase() {
    @BeforeAll
    fun setup() {
        val scanners =
                Scanners(SubTypesScanner(false),
                         TypeAnnotationsScanner(),
                         MethodAnnotationsScanner(),
                         MethodParameterNamesScanner(),
                         MemberUsageScanner())
        val conf1 = scanners.scan(filter = TestModelFilter, urls = setOf(urlForClass(TestModel::class.java)!!))

        conf1.save(userDir.resolve("target/test-classes/META-INF/reflections/testModel-reflections.xml"))

        val scanners1 = Scanners(MethodParameterScanner())
        val conf2 = scanners1.scan(filter = TestModelFilter, urls = setOf(urlForClass(TestModel::class.java)!!))

        conf2.save(userDir.resolve("target/test-classes/META-INF/reflections/testModel-reflections.json"),
                   JsonSerializer)

        configuration =
                Scanners().merge().merge("META-INF/reflections", Include(".*-reflections.json"), JsonSerializer)
        println(JsonSerializer.toString(configuration))
    }

    @Test
    override fun resources() {
        val scanners = Scanners(ResourcesScanner())
        val filter = Filter.Composite(listOf(Include(".*\\.xml"), Include(".*\\.json")))
        val configuration = scanners.scan(filter = filter, urls = setOf(urlForClass(TestModel::class.java)!!))

        assertToStringEqualsSorted(setOf(Datum("META-INF/reflections/resource1-reflections.xml")),
                                   configuration.resources(Pattern.compile(".*resource1-reflections\\.xml")))

        assertToStringEqualsSorted(setOf(Datum("resource1-reflections.xml"),
                                         Datum("resource2-reflections.xml"),
                                         Datum("testModel-reflections.xml"),
                                         Datum("testModel-reflections.json")), configuration.resources())
    }
}
