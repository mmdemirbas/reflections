package org.reflections;

import com.google.common.base.Predicate;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reflections.scanners.*;
import org.reflections.serializers.JsonSerializer;
import org.reflections.util.*;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertThat;
import static org.reflections.util.Utils.index;

/** */
public class ReflectionsCollectTest extends ReflectionsTest {

    @BeforeClass
    public static void init() {
        Reflections ref = new Reflections(new ConfigurationBuilder().addUrls(ClasspathHelper.forClass(TestModel.class))
                                                                    .filterInputsBy(TestModelFilter)
                                                                    .setScanners(new SubTypesScanner(false),
                                                                                 new TypeAnnotationsScanner(),
                                                                                 new MethodAnnotationsScanner(),
                                                                                 new MethodParameterNamesScanner(),
                                                                                 new MemberUsageScanner()));

        ref.save(getUserDir() + "/target/test-classes" + "/META-INF/reflections/testModel-reflections.xml");

        ref = new Reflections(new ConfigurationBuilder().setUrls(Collections.singletonList(ClasspathHelper.forClass(
                TestModel.class))).filterInputsBy(TestModelFilter).setScanners(new MethodParameterScanner()));

        JsonSerializer serializer = new JsonSerializer();
        ref.save(getUserDir() + "/target/test-classes" + "/META-INF/reflections/testModel-reflections.json",
                 serializer);

        reflections = Reflections.collect()
                                 .merge(Reflections.collect("META-INF/reflections",
                                                            new FilterBuilder().include(".*-reflections.json"),
                                                            serializer));
    }

    @Override
    @Test
    public void testResourcesScanner() {
        Predicate<String> filter = new FilterBuilder().include(".*\\.xml").include(".*\\.json");
        Reflections reflections = new Reflections(new ConfigurationBuilder().filterInputsBy(filter)
                                                                            .setScanners(new ResourcesScanner())
                                                                            .setUrls(Collections.singletonList(
                                                                                    ClasspathHelper.forClass(TestModel.class))));

        Set<String> resolved = reflections.getResources(Pattern.compile(".*resource1-reflections\\.xml"));
        assertThat(resolved, are("META-INF/reflections/resource1-reflections.xml"));

        Set<String> resources = reflections.getStore().get(index(ResourcesScanner.class)).keySet();
        assertThat(resources,
                   are("resource1-reflections.xml",
                       "resource2-reflections.xml",
                       "testModel-reflections.xml",
                       "testModel-reflections.json"));
    }
}
