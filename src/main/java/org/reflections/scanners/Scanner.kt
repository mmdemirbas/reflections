package org.reflections.scanners

import org.reflections.Configuration
import org.reflections.Multimap
import org.reflections.adapters.ClassAdapter
import org.reflections.vfs.Vfs.File

/**
 *
 */
interface Scanner {

    var store: Multimap<String, String>
    var configuration: Configuration
    var acceptResult: (fqn: String) -> Boolean

    fun acceptsInput(file: String): Boolean
    fun scan(file: File, classObject: ClassAdapter?): ClassAdapter?
}
