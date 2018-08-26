package org.reflections.adapters

import org.reflections.util.IndexKey
import org.reflections.util.logWarn

val CreateClassAdapter = try {
    // the CreateJavassistClassAdapter is preferred in terms of performance and class loading.
    CreateJavassistClassAdapter
} catch (e: Throwable) {
    logWarn("could not create CreateJavassistClassAdapter, using CreateJavaReflectionClassAdapter", e)
    CreateJavaReflectionClassAdapter
}

interface ClassAdapter {
    val name: String
    val isPublic: Boolean
    val annotations: List<String>
    val fields: List<FieldAdapter>
    val methods: List<MethodAdapter>
    val superclass: String
    val interfaces: List<String>
}

interface FieldAdapter {
    val name: String
    val isPublic: Boolean
    val annotations: List<String>
}

interface MethodAdapter {
    val name: String
    val isPublic: Boolean
    val annotations: List<String>
    val parameters: List<String>
    fun parameterAnnotations(parameterIndex: Int): List<String>
    val returnType: String
    val modifier: String

    fun getMethodFullKey(cls: ClassAdapter): IndexKey = IndexKey("${cls.name}.${name}(${parameters.joinToString()})")
}