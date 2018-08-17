package org.reflections

import org.junit.BeforeClass
import org.reflections.scanners.*
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder

/**  */
class ReflectionsParallelTest : ReflectionsTest() {

    companion object {

        @BeforeClass
        fun init() {
            ReflectionsTest.Companion.reflections =
                    Reflections(ConfigurationBuilder().setUrls(listOfNotNull(ClasspathHelper.forClass(TestModel::class.java))).filterInputsBy(
                            ReflectionsTest.Companion.TestModelFilter.asPredicate()).setScanners(SubTypesScanner(false),
                                                                                                 TypeAnnotationsScanner(),
                                                                                                 FieldAnnotationsScanner(),
                                                                                                 MethodAnnotationsScanner(),
                                                                                                 MethodParameterScanner(),
                                                                                                 MethodParameterNamesScanner(),
                                                                                                 MemberUsageScanner()).useParallelExecutor())
        }
    }
}
