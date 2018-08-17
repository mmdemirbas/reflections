package org.reflections.scanners

import org.reflections.ClassWrapper
import java.lang.annotation.Inherited

/**
 * scans for class's annotations, where @Retention(RetentionPolicy.RUNTIME)
 */
class TypeAnnotationsScanner : AbstractScanner() {

    override fun scan(cls: ClassWrapper) {
        val className = metadataAdapter.getClassName(cls)

        for (annotationType in metadataAdapter.getClassAnnotationNames(cls) as List<String>) {

            if (acceptResult(annotationType) || annotationType == Inherited::class.java.name) { //as an exception, accept Inherited as well
                store!!.put(annotationType, className)
            }
        }
    }

}
