package org.reflections.scanners

import org.reflections.Configuration
import org.reflections.Multimap
import org.reflections.ReflectionsException
import org.reflections.adapters.ClassAdapter
import org.reflections.adapters.ClassAdapterFactory
import org.reflections.vfs.Vfs.File

/**
 *
 */
abstract class AbstractScanner : Scanner {

    override lateinit var configuration: Configuration
    override lateinit var store: Multimap<String, String>
    override var acceptResult: (fqn: String) -> Boolean = { it: String -> true }

    protected val metadataAdapter: ClassAdapterFactory
        get() = configuration.metadataAdapter

    override fun acceptsInput(file: String) = file.endsWith(".class")

    override fun scan(file: File, classObject: ClassAdapter?): ClassAdapter? {
        var classObject = classObject
        if (classObject == null) {
            try {
                classObject = configuration.metadataAdapter(file)
            } catch (e: Exception) {
                throw ReflectionsException("could not create class object from file " + file.relativePath!!, e)
            }

        }
        scan(classObject!!)
        return classObject
    }

    abstract fun scan(cls: ClassAdapter)

    override fun equals(o: Any?) = this === o || o != null && javaClass == o.javaClass

    override fun hashCode() = javaClass.hashCode()
}
