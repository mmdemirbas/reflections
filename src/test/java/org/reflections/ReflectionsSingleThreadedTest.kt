package org.reflections

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.reflections.scanners.FieldAnnotationsScanner
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.serializers.JsonSerializer
import org.reflections.util.urlForClass

/**
 * @author Muhammed Demirbaş
 * @since 2018-08-30 07:08
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReflectionsSingleThreadedTest : ReflectionsTestBase() {
    @BeforeAll
    fun setup() {
        val scanners =
                Scanners(SubTypesScanner(false),
                         TypeAnnotationsScanner(),
                         FieldAnnotationsScanner(),
                         MethodAnnotationsScanner(),
                         MethodParameterScanner(),
                         MethodParameterNamesScanner(),
                         MemberUsageScanner())
        configuration = scanners.scan(filter = TestModelFilter,
                                      urls = setOf(urlForClass(TestModel::class.java)!!))
        println(JsonSerializer.toString(scanners))
    }
}