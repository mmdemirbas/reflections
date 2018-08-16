package org.reflections;

import com.google.common.base.Predicate;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reflections.MyTestModelStore.org.reflections.TestModel$C1;
import org.reflections.MyTestModelStore.org.reflections.TestModel$C2.annotations.org_reflections_TestModel$AC2;
import org.reflections.MyTestModelStore.org.reflections.TestModel$C4.fields.f1;
import org.reflections.MyTestModelStore.org.reflections.TestModel$C4.methods.*;
import org.reflections.scanners.TypeElementsScanner;
import org.reflections.serializers.JavaCodeSerializer;
import org.reflections.util.*;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.reflections.TestModel.*;

/** */
public class JavaCodeSerializerTest {

    @BeforeClass
    public static void generateAndSave() {
        Predicate<String> filter = new FilterBuilder().include("org.reflections.TestModel\\$.*");

        Reflections reflections = new Reflections(new ConfigurationBuilder().filterInputsBy(filter)
                                                                            .setScanners(new TypeElementsScanner().includeFields()
                                                                                                                  .publicOnly(
                                                                                                                          false))
                                                                            .setUrls(Collections.singletonList(
                                                                                    ClasspathHelper.forClass(TestModel.class))));

        //save
        String filename = ReflectionsTest.getUserDir() + "/src/test/java/org.reflections.MyTestModelStore";
        reflections.save(filename, new JavaCodeSerializer());
    }

    @Test
    public void resolve() throws NoSuchMethodException, NoSuchFieldException {
        //class
        assertEquals(C1.class, JavaCodeSerializer.resolveClass(TestModel$C1.class));

        //method
        assertEquals(C4.class.getDeclaredMethod("m1"), JavaCodeSerializer.resolveMethod(m1.class));

        //overloaded method with parameters
        assertEquals(C4.class.getDeclaredMethod("m1", int.class, String[].class),
                     JavaCodeSerializer.resolveMethod(m1_int__java_lang_String$$.class));

        //overloaded method with parameters and multi dimensional array
        assertEquals(C4.class.getDeclaredMethod("m1", int[][].class, String[][].class),
                     JavaCodeSerializer.resolveMethod(m1_int$$$$__java_lang_String$$$$.class));

        //field
        assertEquals(C4.class.getDeclaredField("f1"), JavaCodeSerializer.resolveField(f1.class));

        //annotation
        assertEquals(C2.class.getAnnotation(AC2.class),
                     JavaCodeSerializer.resolveAnnotation(org_reflections_TestModel$AC2.class));
    }
}
