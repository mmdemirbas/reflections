package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
