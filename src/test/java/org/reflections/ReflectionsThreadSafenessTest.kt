package org.reflections

import org.junit.Assert.assertEquals
import org.junit.Test
import org.reflections.scanners.Scanner
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
        val callable = {
            val configuration = Configuration()
            configuration.urls = listOfNotNull(urlForClass(Map::class.java)).toMutableSet()
            configuration.scanners = arrayOf<Scanner>(SubTypesScanner(false)).toSet()
            val reflections = Reflections(configuration)

            reflections.getSubTypesOf(Map::class.java)
        }

        val pool = Executors.newFixedThreadPool(2)

        val first = pool.submit(callable)
        val second = pool.submit(callable)

        assertEquals(first.get(10, SECONDS), second.get(10, SECONDS))
    }
}
