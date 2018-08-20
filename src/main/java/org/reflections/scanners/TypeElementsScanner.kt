package org.reflections.scanners

import org.reflections.ClassWrapper

/**
 * scans fields and methods and stores fqn as key and elements as values
 */
class TypeElementsScanner : AbstractScanner() {

    private var includeFields = true
    private var includeMethods = true
    private var includeAnnotations = true
    private var publicOnly = true

    override fun scan(cls: ClassWrapper) {
        val className = metadataAdapter.getClassName(cls)
        if (!acceptResult(className)) {
            return
        }

        store!!.put(className, "")

        if (includeFields) {
            for (field in metadataAdapter.getFields(cls)) {
                val fieldName = metadataAdapter.getFieldName(field)
                store!!.put(className, fieldName)
            }
        }

        if (includeMethods) {
            for (method in metadataAdapter.getMethods(cls)) {
                if (!publicOnly || metadataAdapter.isPublic(method)) {
                    val methodKey =
                            (metadataAdapter.getMethodName(method) + '('.toString() + metadataAdapter.getParameterNames(
                                    method).joinToString() + ')'.toString())
                    store!!.put(className, methodKey)
                }
            }
        }

        if (includeAnnotations) {
            for (annotation in metadataAdapter.getClassAnnotationNames(cls)) {
                store!!.put(className, "@$annotation")
            }
        }
    }

    @JvmOverloads
    fun includeFields(include: Boolean = true): TypeElementsScanner {
        includeFields = include
        return this
    }

    @JvmOverloads
    fun includeMethods(include: Boolean = true): TypeElementsScanner {
        includeMethods = include
        return this
    }

    @JvmOverloads
    fun includeAnnotations(include: Boolean = true): TypeElementsScanner {
        includeAnnotations = include
        return this
    }

    @JvmOverloads
    fun publicOnly(only: Boolean = true): TypeElementsScanner {
        publicOnly = only
        return this
    }
}
