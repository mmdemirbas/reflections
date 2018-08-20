package org.reflections.scanners

import org.reflections.adapters.ClassAdapter

/**
 * scans fields and methods and stores fqn as key and elements as values
 */
class TypeElementsScanner : AbstractScanner() {

    private var includeFields = true
    private var includeMethods = true
    private var includeAnnotations = true
    private var publicOnly = true

    override fun scan(cls: ClassAdapter) {
        val className = cls.name
        if (!acceptResult(className)) {
            return
        }

        store!!.put(className, "")

        if (includeFields) {
            for (field in cls.fields) {
                val fieldName = field.name
                store!!.put(className, fieldName)
            }
        }

        if (includeMethods) {
            for (method in cls.methods) {
                if (!publicOnly || method.isPublic) {
                    val methodKey =
                            (method.name + '('.toString() + method.parameters.joinToString() + ')'.toString())
                    store!!.put(className, methodKey)
                }
            }
        }

        if (includeAnnotations) {
            for (annotation in cls.annotations) {
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
