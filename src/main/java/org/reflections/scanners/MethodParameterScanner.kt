package org.reflections.scanners

import org.reflections.adapters.ClassAdapter

/**
 * scans methods/constructors and indexes parameters, return type and parameter annotations
 */
class MethodParameterScanner : AbstractScanner() {

    override fun scan(cls: ClassAdapter) {
        val md = metadataAdapter

        for (method in cls.methods) {

            val signature = method.parameters.toString()
            if (acceptResult(signature)) {
                store!!.put(signature, method.getMethodFullKey(cls))
            }

            val returnTypeName = method.returnType
            if (acceptResult(returnTypeName)) {
                store!!.put(returnTypeName, method.getMethodFullKey(cls))
            }

            val parameterNames = method.parameters
            for (i in parameterNames.indices) {
                for (paramAnnotation in method.parameterAnnotations(i)) {
                    if (acceptResult(paramAnnotation)) {
                        store!!.put(paramAnnotation, method.getMethodFullKey(cls))
                    }
                }
            }
        }
    }
}
