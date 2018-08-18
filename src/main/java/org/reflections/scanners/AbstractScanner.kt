package org.reflections.scanners

import org.reflections.*
import org.reflections.adapters.MetadataAdapter
import org.reflections.vfs.Vfs.File

/**
 *
 */
abstract class AbstractScanner : Scanner {

    private var configuration: Configuration? = null
    override var store: Multimap<String, String>? = null
    var resultFilter = { it: String -> true }

    protected val metadataAdapter: MetadataAdapter<ClassWrapper, FieldWrapper, MethodWrapper>
        get() = configuration!!.metadataAdapter

    override fun acceptsInput(file: String): Boolean {
        return metadataAdapter.acceptsInput(file)
    }

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

    //
    fun getConfiguration(): Configuration? {
        return configuration
    }

    override fun setConfiguration(configuration: Configuration) {
        this.configuration = configuration
    }

    override fun filterResultsBy(filter: (String) -> Boolean): Scanner {
        resultFilter = filter
        return this
    }

    //
    override fun acceptResult(fqn: String): Boolean {
        return fqn != null && resultFilter(fqn)
    }

    //
    override fun equals(o: Any?): Boolean {
        return this === o || o != null && javaClass == o.javaClass
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
