package org.reflections;

import org.junit.BeforeClass;
import org.reflections.scanners.*;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.util.Collections;

/** */
public class ReflectionsParallelTest extends ReflectionsTest {

    @BeforeClass
    public static void init() {
        reflections = new Reflections(new ConfigurationBuilder().setUrls(Collections.singletonList(ClasspathHelper.forClass(
                TestModel.class)))
                                                                .filterInputsBy(TestModelFilter)
                                                                .setScanners(new SubTypesScanner(false),
                                                                             new TypeAnnotationsScanner(),
                                                                             new FieldAnnotationsScanner(),
                                                                             new MethodAnnotationsScanner(),
                                                                             new MethodParameterScanner(),
                                                                             new MethodParameterNamesScanner(),
                                                                             new MemberUsageScanner())
                                                                .useParallelExecutor());
    }
}
