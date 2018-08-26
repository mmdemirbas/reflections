package org.reflections

import org.junit.jupiter.api.BeforeAll
import org.reflections.scanners.FieldAnnotationsScanner
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.Scanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.executorService
import org.reflections.util.urlForClass

class ReflectionsParallelTest : ReflectionsTest() {
    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            val configuration = Configuration()
            configuration.urls = listOfNotNull(urlForClass(TestModel::class.java)).toMutableSet()
            configuration.filter = ReflectionsTest.TestModelFilter
            configuration.scanners =
                    arrayOf<Scanner>(SubTypesScanner(false),
                                     TypeAnnotationsScanner(),
                                     FieldAnnotationsScanner(),
                                     MethodAnnotationsScanner(),
                                     MethodParameterScanner(),
                                     MethodParameterNamesScanner(),
                                     MemberUsageScanner()).toSet()
            configuration.executorService = executorService()
            ReflectionsTest.reflections = Reflections(configuration)
        }
    }
}
