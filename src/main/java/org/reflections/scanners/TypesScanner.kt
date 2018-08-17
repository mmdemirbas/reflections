package org.reflections.scanners

import org.reflections.ClassWrapper
import org.reflections.vfs.Vfs.File

/**
 * scans classes and stores fqn as key and full path as value.
 *
 * Deprecated. use [org.reflections.scanners.TypeElementsScanner]
 */
@Deprecated("")
class TypesScanner : AbstractScanner() {

    override fun scan(file: File, classObject: ClassWrapper?): ClassWrapper? {
        var classObject = classObject
        classObject = super.scan(file, classObject)
        val className = metadataAdapter.getClassName(classObject!!)
        store!!.put(className, className)
        return classObject
    }

    override fun scan(cls: ClassWrapper) {
        throw UnsupportedOperationException("should not get here")
    }
}