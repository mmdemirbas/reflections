package org.reflections.scanners

import org.reflections.ClassWrapper

/**
 * scans for method's annotations
 */
class MethodAnnotationsScanner : AbstractScanner() {

    override fun scan(cls: ClassWrapper) {
        for (method in metadataAdapter.getMethods(cls)) {
            for (methodAnnotation in metadataAdapter.getMethodAnnotationNames(method)) {
                if (acceptResult(methodAnnotation)) {
                    store!!.put(methodAnnotation, metadataAdapter.getMethodFullKey(cls, method))
                }
            }
        }
    }
}
