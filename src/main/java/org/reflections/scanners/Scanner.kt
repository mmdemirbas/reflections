package org.reflections.scanners

import org.reflections.ClassWrapper
import org.reflections.Configuration
import org.reflections.Multimap
import org.reflections.vfs.Vfs.File

/**
 *
 */
interface Scanner {

    var store: Multimap<String, String>?

    fun setConfiguration(configuration: Configuration)

    fun filterResultsBy(filter: (String) -> Boolean): Scanner

    fun acceptsInput(file: String): Boolean

    fun scan(file: File, classObject: ClassWrapper?): ClassWrapper?

    fun acceptResult(fqn: String): Boolean
}
