package org.reflections.scanners

import org.reflections.ClassWrapper

/**
 * scans methods/constructors and indexes parameters, return type and parameter annotations
 */
class MethodParameterScanner : AbstractScanner() {

    override fun scan(cls: ClassWrapper) {
        val md = metadataAdapter

        for (method in md.getMethods(cls)) {

            val signature = md.getParameterNames(method).toString()
            if (acceptResult(signature)) {
                store!!.put(signature, md.getMethodFullKey(cls, method))
            }

            val returnTypeName = md.getReturnTypeName(method)
            if (acceptResult(returnTypeName)) {
                store!!.put(returnTypeName, md.getMethodFullKey(cls, method))
            }

            val parameterNames = md.getParameterNames(method)
            for (i in parameterNames.indices) {
                for (paramAnnotation in md.getParameterAnnotationNames(method, i)) {
                    if (acceptResult(paramAnnotation as String)) {
                        store!!.put(paramAnnotation, md.getMethodFullKey(cls, method))
                    }
                }
            }
        }
    }
}
