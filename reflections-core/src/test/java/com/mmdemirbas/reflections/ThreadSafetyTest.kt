package com.mmdemirbas.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThreadSafetyTest {
    /**
     * https://github.com/ronmamo/reflections/issues/81
     */
    @Test
    fun `scan is thread-safe`() {
        // todo: thread-safety testi yanlış yazılmış. Scanner dışarda create edilmeli ve aynı scanner kullanılmalı.
        // Zaten bu şekildeki test her türlü geçer. Halbuki scanner'ların store'ları thread-safe değil.
        // Üstelik ben şu an executorService verilirse her bir store'a multi-threaded erişime izin vermiş oluyor olabilirim.

        // todo: thead-safety testi one-shot olmaz. Çok kere denenmeli, çünkü random sonuç verebilir.
        val callable = {
            SubTypesScanner(excludeObjectClass = false).scan(Map::class.java)
        }

        val pool = Executors.newFixedThreadPool(2)

        val first = pool.submit(callable)
        val second = pool.submit(callable)

        assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
    }

    @Test
    fun `multi-threaded scan gives same result with single-threaded scan`() {
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
