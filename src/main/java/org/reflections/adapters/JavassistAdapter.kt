package org.reflections.adapters

import javassist.bytecode.AccessFlag
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.ClassFile
import javassist.bytecode.Descriptor
import javassist.bytecode.FieldInfo
import javassist.bytecode.MethodInfo
import javassist.bytecode.ParameterAnnotationsAttribute
import org.reflections.util.substringBetween
import org.reflections.util.tryOrThrow
import org.reflections.util.whileNotNull
import org.reflections.vfs.VfsFile
import java.io.BufferedInputStream
import java.io.DataInputStream

val CreateJavassistClassAdapter = { vfsFile: VfsFile ->
    tryOrThrow("could not create class file from ${vfsFile.name}") {
        vfsFile.openInputStream()
            .use { stream -> JavassistClassAdapter(ClassFile(DataInputStream(BufferedInputStream(stream)))) }
    }
}

data class JavassistClassAdapter(val cls: ClassFile) : ClassAdapter {
    override val fields = cls.fields.map { JavassistFieldAdapter(it as FieldInfo) }
    override val methods = cls.methods.map { JavassistMethodAdapter(it as MethodInfo) }

    override val annotations = listOf(AnnotationsAttribute.visibleTag, AnnotationsAttribute.invisibleTag).flatMap {
        (cls.getAttribute(it) as AnnotationsAttribute?)?.annotations.orEmpty().map { it.typeName }
    }

    override val isPublic = AccessFlag.isPublic(cls.accessFlags)
    override val name = cls.name!!
    override val superclass = cls.superclass!!
    override val interfaces = cls.interfaces.map { it!! }
}

data class JavassistFieldAdapter(val field: FieldInfo) : FieldAdapter {
    override val annotations = listOf(AnnotationsAttribute.visibleTag, AnnotationsAttribute.invisibleTag).flatMap {
        (field.getAttribute(it) as AnnotationsAttribute?)?.annotations.orEmpty().map { it.typeName }
    }

    override val name = field.name!!
    override val isPublic = AccessFlag.isPublic(field.accessFlags)
}

data class JavassistMethodAdapter(val method: MethodInfo) : MethodAdapter {
    override val name = method.name!!
    override val parameters = method.descriptor.substringBetween('(', ')').splitDescriptorToTypeNames()

    override val annotations = listOf(AnnotationsAttribute.visibleTag, AnnotationsAttribute.invisibleTag).flatMap {
        (method.getAttribute(it) as AnnotationsAttribute?)?.annotations.orEmpty().map { it.typeName }
    }

    override fun parameterAnnotations(parameterIndex: Int) =
            listOf(ParameterAnnotationsAttribute.visibleTag, ParameterAnnotationsAttribute.invisibleTag).flatMap {
                (method.getAttribute(it) as ParameterAnnotationsAttribute?)?.annotations?.getOrNull(parameterIndex)
                    .orEmpty().map { it.typeName }
            }

    override val returnType = method.descriptor.substringAfterLast(')').splitDescriptorToTypeNames()[0]!!

    override val modifier = when {
        AccessFlag.isPrivate(method.accessFlags)   -> "private"
        AccessFlag.isProtected(method.accessFlags) -> "protected"
        AccessFlag.isPublic(method.accessFlags)    -> "public"
        else                                       -> ""
    }

    override val isPublic = AccessFlag.isPublic(method.accessFlags)

    private fun String.splitDescriptorToTypeNames() = when {
        isEmpty() -> listOf()
        else      -> {
            val iterator = Descriptor.Iterator(this)
            val indices = whileNotNull { if (iterator.hasNext()) iterator.next() else null }.toList() + length
            (0 until indices.size - 1).map {
                Descriptor.toString(substring(indices[it], indices[it + 1]))
            }
        }
    }
}