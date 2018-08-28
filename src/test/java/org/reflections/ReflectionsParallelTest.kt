package org.reflections

import org.junit.jupiter.api.BeforeAll
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

class ReflectionsParallelTest : ReflectionsTest() {
    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
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
}
