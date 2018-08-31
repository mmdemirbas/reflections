package org.reflections

import javassist.bytecode.AccessFlag
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.ClassFile
import javassist.bytecode.Descriptor
import javassist.bytecode.FieldInfo
import javassist.bytecode.MethodInfo
import javassist.bytecode.ParameterAnnotationsAttribute
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

val CreateJavassistClassAdapter = { vfsFile: VfsFile ->
    tryOrThrow("could not create class file from ${vfsFile.name}") {
        vfsFile.openInputStream()
            .use { stream -> JavassistClassAdapter(ClassFile(DataInputStream(BufferedInputStream(stream)))) }
    }
}

val CreateJavaReflectionClassAdapter = { vfsFile: VfsFile ->
    JavaReflectionClassAdapter(classForName(vfsFile.relativePath!!.replace("/",
                                                                           ".").replace(
            ".class",
            ""))!!)
}

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

    fun getMethodFullKey(cls: ClassAdapter) = "${cls.name}.${name}(${parameters.joinToString()})"
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


data class JavaReflectionClassAdapter(val cls: Class<*>) : ClassAdapter {
    override val fields = cls.declaredFields.map { JavaReflectionFieldAdapter(it) }
    override val methods = (cls.declaredMethods.asList() + cls.declaredConstructors).map {
        JavaReflectionMethodAdapter(it)
    }

    override val annotations = cls.declaredAnnotations.map { it.annotationClass.java.name }
    override val name = cls.name!!
    override val superclass = cls.superclass?.name.orEmpty()
    override val interfaces = cls.interfaces.map { it.name!! }
    override val isPublic = Modifier.isPublic(cls.modifiers)
}

data class JavaReflectionFieldAdapter(val field: Field) : FieldAdapter {
    override val annotations = field.declaredAnnotations.map { it.annotationClass.java.name }
    override val name = field.name!!
    override val isPublic = Modifier.isPublic(field.modifiers)
}

data class JavaReflectionMethodAdapter(val method: Member) : MethodAdapter {
    override val name = when (method) {
        is Method         -> method.name
        is Constructor<*> -> "<init>"
        else              -> TODO()
    }

    override val parameters = (method as? Executable)?.parameterTypes.orEmpty().map { cls ->
        when {
            cls.isArray -> tryOrDefault(cls.name) {
                val componentTypes = cls.generateWhileNotNull { componentType }
                componentTypes.last().name + "[]".repeat(componentTypes.size - 1)
            }
            else        -> cls.name
        }!!
    }

    override val annotations = (method as Executable).declaredAnnotations!!.map { it.annotationClass.java.name }

    override fun parameterAnnotations(parameterIndex: Int) =
            (method as Executable).parameterAnnotations[parameterIndex].map { it.annotationClass.java.name }

    override val returnType = (method as Method).returnType.name!!
    override val modifier = Modifier.toString(method.modifiers)!!
    override val isPublic = Modifier.isPublic(method.modifiers)
}