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
import org.reflections.util.executorService
import org.reflections.util.urlForClass

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReflectionsMultiThreadedTest : ReflectionsTestBase() {
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
                                      urls = setOf(urlForClass(TestModel::class.java)!!),
                                      executorService = executorService())
        println(JsonSerializer.toString(scanners))
    }
}
