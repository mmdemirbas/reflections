package org.reflections.adapters

import javassist.bytecode.AccessFlag
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.ClassFile
import javassist.bytecode.Descriptor
import javassist.bytecode.FieldInfo
import javassist.bytecode.MethodInfo
import javassist.bytecode.ParameterAnnotationsAttribute
import org.reflections.ReflectionsException
import org.reflections.substringBetween
import org.reflections.vfs.Vfs
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException

val JavassistFactory = { file: Vfs.File ->
    try {
        file.openInputStream().use { stream ->
            JavassistClassAdapter(ClassFile(DataInputStream(BufferedInputStream(stream))))
        }
    } catch (e: IOException) {
        throw ReflectionsException("could not create class file from " + file.name, e)
    }
}

data class JavassistClassAdapter(val delegate: ClassFile) : ClassAdapter {
    override val fields get() = delegate.fields.map { JavassistFieldAdapter(it as FieldInfo) }
    override val methods get() = delegate.methods.map { JavassistMethodAdapter(it as MethodInfo) }

    override val annotations
        get() = listOf(delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?,
                       delegate.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?).flatMap {
            it?.annotations.orEmpty().map { it.typeName }
        }

    override val isPublic get() = AccessFlag.isPublic(delegate.accessFlags)
    override val name get() = delegate.name!!
    override val superclass get() = delegate.superclass!!
    override val interfaces get() = delegate.interfaces.map { it!! }
}

data class JavassistFieldAdapter(val delegate: FieldInfo) : FieldAdapter {
    override val annotations
        get() = listOf(delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?,
                       delegate.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?).flatMap {
            it?.annotations.orEmpty().map { it.typeName }
        }

    override val name get() = delegate.name!!
    override val isPublic get() = AccessFlag.isPublic(delegate.accessFlags)
}

data class JavassistMethodAdapter(val delegate: MethodInfo) : MethodAdapter {
    override val name get() = delegate.name!!
    override val parameters get() = delegate.descriptor.substringBetween('(', ')').splitDescriptorToTypeNames()

    override val annotations
        get() = listOf(delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?,
                       delegate.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?).flatMap {
            it?.annotations.orEmpty().map { it.typeName }
        }

    override fun parameterAnnotations(parameterIndex: Int) =
            listOf(delegate.getAttribute(ParameterAnnotationsAttribute.visibleTag) as ParameterAnnotationsAttribute?,
                   delegate.getAttribute(ParameterAnnotationsAttribute.invisibleTag) as ParameterAnnotationsAttribute?).flatMap {
                it?.annotations?.getOrNull(parameterIndex).orEmpty().map { it.typeName }
            }

    override val returnType get() = delegate.descriptor.substringAfterLast(')').splitDescriptorToTypeNames()[0]!!

    override val modifier
        get() = when {
            AccessFlag.isPrivate(delegate.accessFlags)   -> "private"
            AccessFlag.isProtected(delegate.accessFlags) -> "protected"
            AccessFlag.isPublic(delegate.accessFlags)    -> "public"
            else                                         -> ""
        }

    override val isPublic get() = AccessFlag.isPublic(delegate.accessFlags)

    private fun String.splitDescriptorToTypeNames() = when {
        isEmpty() -> listOf()
        else      -> {
            val indices = mutableListOf<Int>()
            val iterator = Descriptor.Iterator(this)
            while (iterator.hasNext()) {
                indices.add(iterator.next())
            }
            indices.add(length)
            (0 until indices.size - 1).map {
                Descriptor.toString(substring(indices[it], indices[it + 1]))
            }
        }
    }
}