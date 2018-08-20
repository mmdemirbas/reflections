package org.reflections.scanners

import org.reflections.adapters.ClassAdapter

/**
 * scans for method's annotations
 */
class MethodAnnotationsScanner : AbstractScanner() {

    override fun scan(cls: ClassAdapter) {
        for (method in cls.methods) {
            for (methodAnnotation in method.annotations) {
                if (acceptResult(methodAnnotation)) {
                    store!!.put(methodAnnotation, method.getMethodFullKey(cls))
                }
            }
        }
    }
}
