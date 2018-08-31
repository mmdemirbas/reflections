package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.reflections.scanners.CompositeScanner
import org.reflections.scanners.FieldAnnotationsScanner
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.executorService
import java.util.concurrent.ExecutorService

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReflectionsMultiThreadedTest {
    @Test
    fun setup() {
        val scannerSingleThread = newScanner(null)
        val scannerMultiThread = newScanner(executorService())
        assertEquals(scannerSingleThread, scannerMultiThread)
    }

    private fun newScanner(executorService: ExecutorService?): CompositeScanner {
        return CompositeScanner(SubTypesScanner(excludeObjectClass = false),
                                TypeAnnotationsScanner(),
                                FieldAnnotationsScanner(),
                                MethodAnnotationsScanner(),
                                MethodParameterScanner(),
                                MethodParameterNamesScanner(),
                                MemberUsageScanner()).scan(klass = TestModel::class.java,
                                                           executorService = executorService)
    }
}
