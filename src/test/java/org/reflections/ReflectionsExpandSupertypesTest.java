package org.reflections;

import junit.framework.Assert;
import org.junit.Test;
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.A;
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.B;
import org.reflections.ReflectionsExpandSupertypesTest.TestModel.ScannedScope.C;
import org.reflections.util.*;

import java.util.Set;

public class ReflectionsExpandSupertypesTest {

    private static final String        packagePrefix = "org.reflections.ReflectionsExpandSupertypesTest\\$TestModel\\$ScannedScope\\$.*";
    private final        FilterBuilder inputsFilter  = new FilterBuilder().include(packagePrefix);

    public interface TestModel {

        interface A {} // outside of scanned scope

        interface B extends A {} // outside of scanned scope, but immediate supertype

        interface ScannedScope {

            interface C extends B {}

            interface D extends B {}
        }
    }

    @Test
    public void testExpandSupertypes() {
        Reflections refExpand = new Reflections(new ConfigurationBuilder().
                                                                                  setUrls(ClasspathHelper.forClass(C.class))
                                                                          .
                                                                                  filterInputsBy(inputsFilter));
        Assert.assertTrue(refExpand.getConfiguration().shouldExpandSuperTypes());
        Set<Class<? extends A>> subTypesOf = refExpand.getSubTypesOf(A.class);
        Assert.assertTrue("expanded", subTypesOf.contains(B.class));
        Assert.assertTrue("transitivity", subTypesOf.containsAll(refExpand.getSubTypesOf(B.class)));
    }

    @Test
    public void testNotExpandSupertypes() {
        Reflections refDontExpand = new Reflections(new ConfigurationBuilder().
                                                                                      setUrls(ClasspathHelper.forClass(C.class))
                                                                              .
                                                                                      filterInputsBy(inputsFilter)
                                                                              .
                                                                                      setExpandSuperTypes(false));
        Assert.assertFalse(refDontExpand.getConfiguration().shouldExpandSuperTypes());
        Set<Class<? extends A>> subTypesOf1 = refDontExpand.getSubTypesOf(A.class);
        Assert.assertFalse(subTypesOf1.contains(B.class));
    }
}
