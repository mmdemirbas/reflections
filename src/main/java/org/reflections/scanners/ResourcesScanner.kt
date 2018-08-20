package org.reflections.scanners

import org.reflections.adapters.ClassAdapter
import org.reflections.vfs.Vfs.File

/**
 * collects all resources that are not classes in a collection
 *
 * key: value - {web.xml: WEB-INF/web.xml}
 */
class ResourcesScanner : AbstractScanner() {

    override fun acceptsInput(file: String): Boolean {
        return !file.endsWith(".class") //not a class
    }

    override fun scan(file: File, classObject: ClassAdapter?): ClassAdapter? {
        store!!.put(file.name, file.relativePath!!)
        return classObject
    }

    override fun scan(cls: ClassAdapter) {
        throw UnsupportedOperationException() //shouldn't get here
    }
}
