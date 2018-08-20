package org.reflections

import org.junit.BeforeClass
import org.reflections.scanners.FieldAnnotationsScanner
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder

/**  */
class ReflectionsParallelTest : ReflectionsTest() {

    companion object {

        @BeforeClass
        fun init() {
            ReflectionsTest.reflections =
                    Reflections(ConfigurationBuilder().setUrls(listOfNotNull(ClasspathHelper.forClass(TestModel::class.java))).filterInputsBy(
                            ReflectionsTest.TestModelFilter.asPredicate()).setScanners(SubTypesScanner(false),
                                                                                       TypeAnnotationsScanner(),
                                                                                       FieldAnnotationsScanner(),
                                                                                       MethodAnnotationsScanner(),
                                                                                       MethodParameterScanner(),
                                                                                       MethodParameterNamesScanner(),
                                                                                       MemberUsageScanner()).useParallelExecutor())
        }
    }
}
