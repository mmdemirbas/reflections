package org.reflections;

import org.junit.Assert;
import org.junit.Test;
import org.reflections.util.ClasspathHelper;

import java.net.*;
import java.util.*;

/**
 * Test ClasspathHelper utility class
 */
public final class ClasspathHelperTest {

    @Test
    public void testForClassLoaderShouldntReorderUrls() throws MalformedURLException {
        // testing same URL set with different order to not fall into the case when HashSet orders elements in the same order as we do
        URL[] urls1 = {new URL("file", "foo", 1111, "foo"),
                       new URL("file", "bar", 1111, "bar"),
                       new URL("file", "baz", 1111, "baz")};
        List<URL> urlsList2 = Arrays.asList(urls1);
        Collections.reverse(urlsList2);
        URL[] urls2 = urlsList2.toArray(new URL[urlsList2.size()]);

        URLClassLoader  urlClassLoader1 = new URLClassLoader(urls1, null);
        URLClassLoader  urlClassLoader2 = new URLClassLoader(urls2, null);
        Collection<URL> resultUrls1     = ClasspathHelper.forClassLoader(urlClassLoader1);
        Collection<URL> resultUrls2     = ClasspathHelper.forClassLoader(urlClassLoader2);

        Assert.assertArrayEquals("URLs returned from forClassLoader should be in the same order as source URLs",
                                 urls1,
                                 resultUrls1.toArray());
        Assert.assertArrayEquals("URLs returned from forClassLoader should be in the same order as source URLs",
                                 urls2,
                                 resultUrls2.toArray());
    }
}
