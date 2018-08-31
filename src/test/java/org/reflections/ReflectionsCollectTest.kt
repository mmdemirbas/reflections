package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.reflections.Filter.Include
import org.reflections.scanners.CompositeScanner
import org.reflections.scanners.MemberUsageScanner
import org.reflections.scanners.MethodAnnotationsScanner
import org.reflections.scanners.MethodParameterNamesScanner
import org.reflections.scanners.MethodParameterScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.serializers.JsonSerializer
import org.reflections.serializers.XmlSerializer
import java.nio.file.FileSystems

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReflectionsCollectTest {

    @Test
    fun `collect XML`() {
        val fileSystem = FileSystems.getDefault()
        val scanned =
                CompositeScanner(SubTypesScanner(excludeObjectClass = false),
                                 TypeAnnotationsScanner(),
                                 MethodAnnotationsScanner(),
                                 MethodParameterNamesScanner(),
                                 MemberUsageScanner()).scan(TestModel::class.java)
        scanned.save(path = userDir.resolve("target/test-classes/META-INF/reflections/testModel-reflections.xml"),
                     serializer = XmlSerializer)
        val collected = CompositeScanner.collect(fileSystem = fileSystem)
        assertEquals(scanned, collected)
    }

    @Test
    fun `collect JSON`() {
        val fileSystem = FileSystems.getDefault()
        val scanned = MethodParameterScanner().scan(TestModel::class.java)
        scanned.save(path = userDir.resolve("target/test-classes/META-INF/reflections/testModel-reflections.json"),
                     serializer = JsonSerializer)
        val collected =
                CompositeScanner.collect(packagePrefix = "META-INF/reflections",
                                         resourceNameFilter = Include(".*-reflections\\.json"),
                                         serializer = JsonSerializer,
                                         fileSystem = fileSystem)
        assertEquals(scanned, collected)
    }

    @Test
    fun merge() {
        TODO("not implemented")
    }
}
