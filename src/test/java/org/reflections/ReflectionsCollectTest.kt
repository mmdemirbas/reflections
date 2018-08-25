package org.reflections

import org.junit.Assert.assertThat
import org.junit.BeforeClass
import org.junit.Test
import org.reflections.Filter.Include
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.ResourcesScanner
import org.reflections.scanners.Scanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.serializers.JsonSerializer
import org.reflections.util.IndexKey
import org.reflections.util.urlForClass
import java.util.regex.Pattern

class ReflectionsCollectTest : ReflectionsTest() {
    @Test
    override fun testResourcesScanner() {
        val filter = Filter.Composite(listOf(Include(".*\\.xml"), Include(".*\\.json")))
        val configuration = Configuration()
        configuration.filter = filter
        configuration.scanners = arrayOf<Scanner>(ResourcesScanner()).toSet()
        configuration.urls = listOfNotNull(urlForClass(TestModel::class.java)).toMutableSet()
        val reflections = Reflections(configuration)

        val resolved = reflections.getResources(Pattern.compile(".*resource1-reflections\\.xml"))
        assertThat(resolved, ReflectionsTest.are(IndexKey("META-INF/reflections/resource1-reflections.xml")))

        val resources = reflections.stores.getOrThrow(ResourcesScanner::class).keys()
        assertThat(resources,
                   ReflectionsTest.are(IndexKey("resource1-reflections.xml"),
                                       IndexKey("resource2-reflections.xml"),
                                       IndexKey("testModel-reflections.xml"),
                                       IndexKey("testModel-reflections.json")))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun init() {
            val configuration = Configuration()
            configuration.urls += setOf(urlForClass(TestModel::class.java)!!)
            configuration.filter = ReflectionsTest.TestModelFilter
            configuration.scanners =
                    arrayOf<Scanner>(SubTypesScanner(false),
                                     TypeAnnotationsScanner(),
                                     MethodAnnotationsScanner(),
                                     MethodParameterNamesScanner(),
                                     MemberUsageScanner()).toSet()
            var ref = Reflections(configuration)

            ref.save("${ReflectionsTest.userDir}/target/test-classes/META-INF/reflections/testModel-reflections.xml")

            val configuration1 = Configuration()
            configuration1.urls = listOfNotNull(urlForClass(TestModel::class.java)).toMutableSet()
            configuration1.filter = ReflectionsTest.TestModelFilter
            configuration1.scanners = arrayOf<Scanner>(MethodParameterScanner()).toSet()
            ref = Reflections(configuration1)

            val serializer = JsonSerializer
            ref.save("${ReflectionsTest.userDir}/target/test-classes/META-INF/reflections/testModel-reflections.json",
                     serializer)

            ReflectionsTest.reflections = Reflections()
            reflections.merge()
            reflections.merge("META-INF/reflections", Include(".*-reflections.json"), serializer)
        }
    }
}
