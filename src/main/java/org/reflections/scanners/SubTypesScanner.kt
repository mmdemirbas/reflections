package org.reflections.scanners

import org.reflections.ClassWrapper
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
            filterResultsBy(FilterBuilder().exclude(Any::class.java.name).asPredicate()) //exclude direct Object subtypes
        }
    }

    override fun scan(cls: ClassWrapper) {
        val className = metadataAdapter.getClassName(cls)
        val superclass = metadataAdapter.getSuperclassName(cls)

        if (acceptResult(superclass)) {
            store!!.put(superclass, className)
        }

        for (anInterface in metadataAdapter.getInterfacesNames(cls) as List<String>) {
            if (acceptResult(anInterface)) {
                store!!.put(anInterface, className)
            }
        }
    }
}
/**
 * created new SubTypesScanner. will exclude direct Object subtypes
 */ //exclude direct Object subtypes by default
