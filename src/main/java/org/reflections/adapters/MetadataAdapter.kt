package org.reflections.adapters

import org.reflections.vfs.Vfs

typealias  ClassAdapterFactory = (file: Vfs.File) -> ClassAdapter?

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
    val name: String?
    val isPublic: Boolean
    val annotations: List<String>
    val parameters: List<String>
    fun parameterAnnotations(parameterIndex: Int): List<String>
    val returnType: String
    val modifier: String

    fun getMethodFullKey(cls: ClassAdapter): String = "${cls.name}.${name}(${parameters.joinToString()})"
}