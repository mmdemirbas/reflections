package org.reflections;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.util.Set;
import java.util.concurrent.*;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

public class ReflectionsThreadSafenessTest {

    /**
     * https://github.com/ronmamo/reflections/issues/81
     */
    @Test
    public void reflections_scan_is_thread_safe() throws Exception {

        Callable<Set<Class<? extends ImmutableMap>>> callable = () -> {
            Reflections reflections = new Reflections(new ConfigurationBuilder().setUrls(singletonList(ClasspathHelper.forClass(
                    ImmutableMap.class))).setScanners(new SubTypesScanner(false)));

            return reflections.getSubTypesOf(ImmutableMap.class);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> first  = pool.submit(callable);
        Future<?> second = pool.submit(callable);

        assertEquals(first.get(5, SECONDS), second.get(5, SECONDS));
    }
}
