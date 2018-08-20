package org.reflections.scanners

import org.reflections.adapters.ClassAdapter
import java.lang.annotation.Inherited

/**
 * scans for class's annotations, where @Retention(RetentionPolicy.RUNTIME)
 */
class TypeAnnotationsScanner : AbstractScanner() {

    override fun scan(cls: ClassAdapter) {
        val className = cls.name

        for (annotationType in cls.annotations) {

            if (acceptResult(annotationType) || annotationType == Inherited::class.java.name) { //as an exception, accept Inherited as well
                store!!.put(annotationType, className)
            }
        }
    }

}
