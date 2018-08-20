package org.reflections.adapters

import org.reflections.ClassWrapper
import org.reflections.FieldWrapper
import org.reflections.MethodWrapper
import org.reflections.vfs.Vfs.File

/**
 *
 */
interface MetadataAdapter<C : ClassWrapper, F : FieldWrapper, M : MethodWrapper> {

    fun getClassName(cls: C): String
    fun getSuperclassName(cls: C): String
    fun getInterfacesNames(cls: C): List<String>
    fun getFields(cls: C): List<F>
    fun getMethods(cls: C): List<M>
    fun getMethodName(method: M): String?
    fun getParameterNames(method: M): List<String>
    fun getClassAnnotationNames(aClass: C): List<String>
    fun getFieldAnnotationNames(field: F): List<String>
    fun getMethodAnnotationNames(method: M): List<String>
    fun getParameterAnnotationNames(method: M, parameterIndex: Int): List<String>
    fun getReturnTypeName(method: M): String
    fun getFieldName(field: F): String
    fun getOrCreateClassObject(file: File): C?
    fun getMethodModifier(method: M): String
    fun getMethodKey(cls: C, method: M): String
    fun getMethodFullKey(cls: C, method: M): String
    fun isPublic(o: Any?): Boolean
    fun acceptsInput(file: String): Boolean
}
