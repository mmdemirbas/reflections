package org.reflections

import org.junit.Assert.assertThat
import org.junit.BeforeClass
import org.junit.Test
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.ResourcesScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.serializers.JsonSerializer
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.reflections.util.Utils.index
import java.util.regex.Pattern

/**  */
class ReflectionsCollectTest : ReflectionsTest() {

    @Test
    override fun testResourcesScanner() {
        val filter = FilterBuilder().include(".*\\.xml").include(".*\\.json").asPredicate()
        val reflections =
                Reflections(ConfigurationBuilder().filterInputsBy(filter).setScanners(ResourcesScanner()).setUrls(
                        listOfNotNull(ClasspathHelper.forClass(TestModel::class.java))))

        val resolved = reflections.getResources(Pattern.compile(".*resource1-reflections\\.xml"))
        assertThat(resolved, ReflectionsTest.are("META-INF/reflections/resource1-reflections.xml"))

        val resources = reflections.store[index(ResourcesScanner::class.java)].keySet()
        assertThat(resources,
                   ReflectionsTest.are("resource1-reflections.xml",
                                       "resource2-reflections.xml",
                                       "testModel-reflections.xml",
                                       "testModel-reflections.json"))
    }

    companion object {

        @BeforeClass
        fun init() {
            var ref =
                    Reflections(ConfigurationBuilder().addUrls(ClasspathHelper.forClass(TestModel::class.java)!!).filterInputsBy(
                            ReflectionsTest.TestModelFilter.asPredicate()).setScanners(SubTypesScanner(false),
                                                                                       TypeAnnotationsScanner(),
                                                                                       MethodAnnotationsScanner(),
                                                                                       MethodParameterNamesScanner(),
                                                                                       MemberUsageScanner()))

            ref.save(ReflectionsTest.userDir + "/target/test-classes" + "/META-INF/reflections/testModel-reflections.xml")

            ref =
                    Reflections(ConfigurationBuilder().setUrls(listOfNotNull(ClasspathHelper.forClass(TestModel::class.java))).filterInputsBy(
                            ReflectionsTest.TestModelFilter.asPredicate()).setScanners(MethodParameterScanner()))

            val serializer = JsonSerializer()
            ref.save(ReflectionsTest.userDir + "/target/test-classes" + "/META-INF/reflections/testModel-reflections.json",
                     serializer)

            ReflectionsTest.reflections =
                    Reflections.collect()!!.merge(Reflections.collect("META-INF/reflections",
                                                                      FilterBuilder().include(".*-reflections.json").asPredicate(),
                                                                      serializer)!!)
        }
    }
}
