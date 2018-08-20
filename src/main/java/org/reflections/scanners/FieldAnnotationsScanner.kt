package org.reflections.scanners

import org.reflections.ClassWrapper

/**
 * scans for field's annotations
 */
class FieldAnnotationsScanner : AbstractScanner() {

    override fun scan(cls: ClassWrapper) {
        val className = metadataAdapter.getClassName(cls)
        metadataAdapter.getFields(cls).forEach { field ->
            metadataAdapter.getFieldAnnotationNames(field).filter { acceptResult(it) }
                .forEach { store?.put(it, String.format("%s.%s", className, metadataAdapter.getFieldName(field))) }
        }
    }
}
