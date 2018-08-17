package org.reflections

import com.google.common.collect.ImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class ReflectionsThreadSafenessTest {

    /**
     * https://github.com/ronmamo/reflections/issues/81
     */
    @Test
    @Throws(Exception::class)
    fun reflections_scan_is_thread_safe() {

        val callable = {
            val reflections =
                    Reflections(ConfigurationBuilder().setUrls(listOfNotNull(ClasspathHelper.forClass(ImmutableMap::class.java))).setScanners(
                            SubTypesScanner(false)))

            reflections.getSubTypesOf(ImmutableMap::class.java)
        }

        val pool = Executors.newFixedThreadPool(2)

        val first = pool.submit(callable)
        val second = pool.submit(callable)

        assertEquals(first.get(5, SECONDS), second.get(5, SECONDS))
    }
}
