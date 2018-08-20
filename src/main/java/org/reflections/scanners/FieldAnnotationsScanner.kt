package org.reflections.scanners

import org.reflections.adapters.ClassAdapter

/**
 * scans for field's annotations
 */
class FieldAnnotationsScanner : AbstractScanner() {

    override fun scan(cls: ClassAdapter) {
        val className = cls.name
        cls.fields.forEach { field ->
            field.annotations.filter { acceptResult(it) }
                .forEach { store?.put(it, String.format("%s.%s", className, field.name)) }
        }
    }
}
