package org.reflections.scanners

import org.reflections.ClassWrapper

/**
 * scans for field's annotations
 */
class FieldAnnotationsScanner : AbstractScanner() {

    override fun scan(cls: ClassWrapper) {
        val className = metadataAdapter.getClassName(cls)
        val fields = metadataAdapter.getFields(cls)
        for (field in fields) {
            val fieldAnnotations = metadataAdapter.getFieldAnnotationNames(field)
            for (fieldAnnotation in fieldAnnotations) {

                if (acceptResult(fieldAnnotation)) {
                    val fieldName = metadataAdapter.getFieldName(field)
                    store?.put(fieldAnnotation, String.format("%s.%s", className, fieldName))
                }
            }
        }
    }
}
