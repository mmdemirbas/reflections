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
class ReflectionsParallelTest : ReflectionsTest() {
    @BeforeAll
    override fun setup() {
        configuration =
                Configuration(scanners = setOf(SubTypesScanner(false),
                                               TypeAnnotationsScanner(),
                                               FieldAnnotationsScanner(),
                                               MethodAnnotationsScanner(),
                                               MethodParameterScanner(),
                                               MethodParameterNamesScanner(),
                                               MemberUsageScanner()),
                              filter = TestModelFilter,
                              urls = setOf(urlForClass(TestModel::class.java)!!),
                              executorService = executorService()).withScan()
        println(JsonSerializer.toString(configuration))
    }
}
