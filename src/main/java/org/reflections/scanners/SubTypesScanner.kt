package org.reflections.scanners

import org.reflections.adapters.ClassAdapter
import org.reflections.util.FilterBuilder

/**
 * scans for superclass and interfaces of a class, allowing a reverse lookup for subtypes
 */
class SubTypesScanner
/**
 * created new SubTypesScanner.
 *
 * @param excludeObjectClass if false, include direct [Object] subtypes in results.
 */
@JvmOverloads constructor(excludeObjectClass: Boolean = true) : AbstractScanner() {

    init {
        if (excludeObjectClass) {
            //exclude direct Object subtypes
            acceptResult = FilterBuilder().exclude(Any::class.java.name).asPredicate()
        }
    }

    override fun scan(cls: ClassAdapter) {
        val className = cls.name
        val superclass = cls.superclass

        if (acceptResult(superclass)) {
            store!!.put(superclass, className)
        }

        for (anInterface in cls.interfaces) {
            if (acceptResult(anInterface)) {
                store!!.put(anInterface, className)
            }
        }
    }
}
/**
 * created new SubTypesScanner. will exclude direct Object subtypes
 */ //exclude direct Object subtypes by default
