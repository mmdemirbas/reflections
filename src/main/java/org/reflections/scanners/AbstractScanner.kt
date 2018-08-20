package org.reflections.scanners

import org.reflections.ClassWrapper
import org.reflections.Configuration
import org.reflections.FieldWrapper
import org.reflections.MethodWrapper
import org.reflections.Multimap
import org.reflections.ReflectionsException
import org.reflections.adapters.MetadataAdapter
import org.reflections.vfs.Vfs.File

/**
 *
 */
abstract class AbstractScanner : Scanner {

    override var configuration: Configuration? = null
    override var store: Multimap<String, String>? = null
    var resultFilter = { it: String -> true }

    protected val metadataAdapter: MetadataAdapter<ClassWrapper, FieldWrapper, MethodWrapper>
        get() = configuration!!.metadataAdapter

    override fun acceptsInput(file: String) = metadataAdapter.acceptsInput(file)

    override fun scan(file: File, classObject: ClassWrapper?): ClassWrapper? {
        var classObject = classObject
        if (classObject == null) {
            try {
                classObject = configuration!!.metadataAdapter.getOrCreateClassObject(file)
            } catch (e: Exception) {
                throw ReflectionsException("could not create class object from file " + file.relativePath!!, e)
            }

        }
        scan(classObject!!)
        return classObject
    }

    abstract fun scan(cls: ClassWrapper)

    override fun filterResultsBy(filter: (String) -> Boolean): Scanner {
        resultFilter = filter
        return this
    }

    override fun acceptResult(fqn: String) = resultFilter(fqn)

    override fun equals(o: Any?) = this === o || o != null && javaClass == o.javaClass

    override fun hashCode() = javaClass.hashCode()
}
