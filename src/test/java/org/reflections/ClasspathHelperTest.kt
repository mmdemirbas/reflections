package org.reflections

import org.junit.Assert
import org.junit.Test
import org.reflections.util.ClasspathHelper
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader

/**
 * Test ClasspathHelper utility class
 */
class ClasspathHelperTest {

    @Test
    @Throws(MalformedURLException::class)
    fun testForClassLoaderShouldntReorderUrls() {
        // testing same URL set with different order to not fall into the case when HashSet orders elements in the same order as we do
        val urls1 =
                arrayOf(URL("file", "foo", 1111, "foo"),
                        URL("file", "bar", 1111, "bar"),
                        URL("file", "baz", 1111, "baz"))
        val urls2 = urls1.reversed().toTypedArray()

        val urlClassLoader1 = URLClassLoader(urls1, null)
        val urlClassLoader2 = URLClassLoader(urls2, null)
        val resultUrls1 = ClasspathHelper.forClassLoader(urlClassLoader1)
        val resultUrls2 = ClasspathHelper.forClassLoader(urlClassLoader2)

        Assert.assertArrayEquals("URLs returned from forClassLoader should be in the same order as source URLs",
                                 urls1,
                                 resultUrls1.toTypedArray())
        Assert.assertArrayEquals("URLs returned from forClassLoader should be in the same order as source URLs",
                                 urls2,
                                 resultUrls2.toTypedArray())
    }
}
