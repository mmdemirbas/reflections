package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems

/**
 * @author Muhammed Demirbaş
 * @since 2018-09-01 14:35
 */
class CompositeScannerTest {
    @Nested
    inner class Collect {
        @Test
        fun `collect XML`() {
            val fileSystem = FileSystems.getDefault()
            val scanned =
                    CompositeScanner(SubTypesScanner(excludeObjectClass = false),
                                     TypeAnnotationsScanner(),
                                     MethodAnnotationsScanner(),
                                     MethodParameterNamesScanner(),
                                     MemberUsageScanner()).scan(TestModel::class.java).dump(XmlSerializer)
                        .save(path = userDir.resolve("target/test-classes/META-INF/reflections/testModel/reflections.xml"),
                              serializer = XmlSerializer)
            val collected =
                    CompositeScanner.collect(packagePrefix = "META-INF/reflections/testModel",
                                             resourceNameFilter = Filter.Include(".*\\.xml"),
                                             fileSystem = fileSystem)
            assertEquals(scanned, collected)
        }

        @Test
        fun `collect JSON`() {
            val fileSystem = FileSystems.getDefault()
            val scanned =
                    MethodParameterScanner().scan(TestModel::class.java).dump(JsonSerializer)
                        .save(path = userDir.resolve("target/test-classes/META-INF/reflections/testModel-reflections.json"),
                              serializer = JsonSerializer)
            val collected =
                    CompositeScanner.collect(packagePrefix = "META-INF/reflections",
                                             resourceNameFilter = Filter.Include(".*-reflections\\.json"),
                                             serializer = JsonSerializer,
                                             fileSystem = fileSystem)
            assertEquals(CompositeScanner(scanned), collected)
        }
    }
}