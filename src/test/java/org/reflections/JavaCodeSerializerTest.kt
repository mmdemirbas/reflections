package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.reflections.Filter.Include
import org.reflections.MyTestModelStore.org.reflections.`TestModel$C1`
import org.reflections.MyTestModelStore.org.reflections.`TestModel$C4`.fields.f1
import org.reflections.MyTestModelStore.org.reflections.`TestModel$C4`.methods.`m1_int$$$$__java_lang_String$$$$`
import org.reflections.MyTestModelStore.org.reflections.`TestModel$C4`.methods.`m1_int__java_lang_String$$`
import org.reflections.MyTestModelStore.org.reflections.`TestModel$C4`.methods.m1
import org.reflections.TestModel.AC2
import org.reflections.TestModel.C1
import org.reflections.TestModel.C2
import org.reflections.TestModel.C4
import org.reflections.scanners.Scanner
import org.reflections.scanners.TypeElementsScanner
import org.reflections.serializers.JavaCodeSerializer
import org.reflections.util.urlForClass

class JavaCodeSerializerTest {
    @Test
    fun resolve() {
        //class
        assertEquals(C1::class.java, JavaCodeSerializer.resolveClass(`TestModel$C1`::class.java))

        //method
        assertEquals(C4::class.java.getDeclaredMethod("m1"), JavaCodeSerializer.resolveMethod(m1::class.java))

        //overloaded method with parameters
        assertEquals(C4::class.java.getDeclaredMethod("m1", Int::class.javaPrimitiveType, Array<String>::class.java),
                     JavaCodeSerializer.resolveMethod(`m1_int__java_lang_String$$`::class.java))

        //overloaded method with parameters and multi dimensional array
        assertEquals(C4::class.java.getDeclaredMethod("m1",
                                                      Array<IntArray>::class.java,
                                                      Array<Array<String>>::class.java),
                     JavaCodeSerializer.resolveMethod(`m1_int$$$$__java_lang_String$$$$`::class.java))

        //field
        assertEquals(C4::class.java.getDeclaredField("f1"), JavaCodeSerializer.resolveField(f1::class.java))

        //annotation
        assertEquals(C2::class.java.getAnnotation(AC2::class.java),
                     JavaCodeSerializer.resolveAnnotation(MyTestModelStore.org.reflections.`TestModel$C2`.annotations.`org_reflections_TestModel$AC2`::class.java))
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun generateAndSave() {
            val configuration = Configuration()
            configuration.filter = Include("org.reflections.TestModel\\$.*")
            configuration.scanners = arrayOf<Scanner>(TypeElementsScanner(publicOnly = false)).toSet()
            configuration.urls = listOfNotNull(urlForClass(TestModel::class.java)).toMutableSet()
            val reflections = Reflections(configuration)

            //save
            val filename = "${ReflectionsTest.userDir}/src/test/java/org.reflections.MyTestModelStore"
            reflections.save(filename, JavaCodeSerializer)
        }
    }
}
