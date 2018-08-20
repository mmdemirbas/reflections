package org.reflections.scanners

import org.reflections.adapters.ClassAdapter
import org.reflections.vfs.Vfs.File

/**
 * scans classes and stores fqn as key and full path as value.
 *
 * Deprecated. use [org.reflections.scanners.TypeElementsScanner]
 */
@Deprecated("")
class TypesScanner : AbstractScanner() {

    override fun scan(file: File, classObject: ClassAdapter?): ClassAdapter? {
        var classObject = classObject
        classObject = super.scan(file, classObject)
        val className = classObject!!.name
        store!!.put(className, className)
        return classObject
    }

    override fun scan(cls: ClassAdapter) {
        throw UnsupportedOperationException("should not get here")
    }
}