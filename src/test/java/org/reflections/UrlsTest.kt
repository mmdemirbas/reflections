package org.reflections

import org.junit.Assert.assertEquals
import org.junit.Test
import org.reflections.util.urlForClassLoader
import java.net.URL
import java.net.URLClassLoader

/**
 * Test Urls utility class
 */
class UrlsTest {
    @Test
    fun testForClassLoaderShouldntReorderUrls() {
        // testing same URL set with different order to not fall into the case when HashSet orders elements in the same order as we do
        val urls1 =
                listOf(URL("file", "foo", 1111, "foo"),
                       URL("file", "bar", 1111, "bar"),
                       URL("file", "baz", 1111, "baz"))
        val urls2 = urls1.reversed()

        val urlClassLoader1 = URLClassLoader(urls1.toTypedArray(), null)
        val urlClassLoader2 = URLClassLoader(urls2.toTypedArray(), null)
        val resultUrls1 = urlForClassLoader(listOf(urlClassLoader1))
        val resultUrls2 = urlForClassLoader(listOf(urlClassLoader2))

        assertEquals("URLs returned from urlForClassLoader should be in the same order as source URLs",
                     urls1,
                     resultUrls1.toList())
        assertEquals("URLs returned from urlForClassLoader should be in the same order as source URLs",
                     urls2,
                     resultUrls2.toList())
    }
}
