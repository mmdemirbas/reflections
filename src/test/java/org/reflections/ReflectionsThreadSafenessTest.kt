package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.urlForClass
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class ReflectionsThreadSafenessTest {
    /**
     * https://github.com/ronmamo/reflections/issues/81
     */
    @Test
    fun reflections_scan_is_thread_safe() {
        val scanners = Scanners(SubTypesScanner(false))
        val callable = {
            scanners.scan(urls = setOf(urlForClass(Map::class.java)!!))
                .subTypesOf(Map::class.java)
        }

        val pool = Executors.newFixedThreadPool(2)

        val first = pool.submit(callable)
        val second = pool.submit(callable)

        assertEquals(first.get(10, SECONDS), second.get(10, SECONDS))
    }
}
