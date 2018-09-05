package com.mmdemirbas.reflections

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
import java.lang.reflect.Method

val createClassAdapter = { virtualFile: VirtualFile ->
    // the Javassist is preferred in terms of performance and class loading.
    try {
        virtualFile.openInputStream()
            .use { stream -> JavassistClassAdapter(ClassFile(DataInputStream(BufferedInputStream(stream)))) }
    } catch (e: Exception) {
        JavaReflectionClassAdapter(classForName(virtualFile.relativePath!!.replace("/", ".").replace(".class", ""))!!)
    }
}

interface ClassAdapter {
    val name: String
    val annotations: List<String>
    val fields: List<FieldAdapter>
    val methods: List<MethodAdapter>
    val superclass: String
    val interfaces: List<String>
}

interface FieldAdapter {
    val name: String
    val annotations: List<String>
}

interface MethodAdapter {
    val name: String
    val annotations: List<String>
    val parameters: List<ParameterAdapter>
    val returnType: String
    val signature: String
}

interface ParameterAdapter {
    val type: String
    val annotations: List<String>
}


private data class JavassistClassAdapter(val cls: ClassFile) : ClassAdapter {
    override val fields = cls.fields.map { JavassistFieldAdapter(it as FieldInfo) }
    override val methods = cls.methods.map { JavassistMethodAdapter(it as MethodInfo, name) }

    override val annotations = listOf(AnnotationsAttribute.visibleTag, AnnotationsAttribute.invisibleTag).flatMap {
        (cls.getAttribute(it) as AnnotationsAttribute?)?.annotations.orEmpty().map { it.typeName }
    }

    override val name = cls.name!!
    override val superclass = cls.superclass!!
    override val interfaces = cls.interfaces.map { it!! }
}

private data class JavassistFieldAdapter(val field: FieldInfo) : FieldAdapter {
    override val annotations = listOf(AnnotationsAttribute.visibleTag, AnnotationsAttribute.invisibleTag).flatMap {
        (field.getAttribute(it) as AnnotationsAttribute?)?.annotations.orEmpty().map { it.typeName }
    }

    override val name = field.name!!
}

data class JavassistMethodAdapter(val method: MethodInfo, val ownerClassName: String) : MethodAdapter {
    override val name = method.name!!
    override val annotations = listOf(AnnotationsAttribute.visibleTag, AnnotationsAttribute.invisibleTag).flatMap {
        (method.getAttribute(it) as AnnotationsAttribute?)?.annotations.orEmpty().map { it.typeName }
    }

    override val parameters = run {
        val types = method.descriptor.substringBetween('(', ')').splitDescriptorToTypeNames()
        val visibles = paramAnnotations(ParameterAnnotationsAttribute.visibleTag)
        val invisibles = paramAnnotations(ParameterAnnotationsAttribute.invisibleTag)
        val mergedAnnotations = visibles.zipWithPadding(invisibles) { v, i -> v.orEmpty() + i.orEmpty() }
        mergeParameters(types, mergedAnnotations)
    }

    private fun paramAnnotations(tag: String) =
            (method.getAttribute(tag) as ParameterAnnotationsAttribute?)?.annotations.orEmpty().map { it.map { it.typeName } }

    override val returnType = method.descriptor.substringAfterLast(')').splitDescriptorToTypeNames()[0]!!

    private fun String.splitDescriptorToTypeNames() = when {
        isEmpty() -> listOf()
        else      -> {
            val iterator = Descriptor.Iterator(this)
            val indices = whileNotNull { mapIf(iterator.hasNext()) { iterator.next() } }.toList() + length
            (0 until indices.size - 1).map { Descriptor.toString(substring(indices[it], indices[it + 1])) }
        }
    }

    override val signature = "$ownerClassName.$name(${parameters.joinToString()})"
}


private data class JavaReflectionClassAdapter(val cls: Class<*>) : ClassAdapter {
    override val fields = cls.declaredFields.map { JavaReflectionFieldAdapter(it) }
    override val methods = (cls.declaredMethods.asList() + cls.declaredConstructors).map {
        JavaReflectionMethodAdapter(it, name)
    }

    override val annotations = cls.declaredAnnotations.map { it.annotationClass.java.name }
    override val name = cls.name!!
    override val superclass = cls.superclass?.name.orEmpty()
    override val interfaces = cls.interfaces.map { it.name!! }
}

private data class JavaReflectionFieldAdapter(val field: Field) : FieldAdapter {
    override val annotations = field.declaredAnnotations.map { it.annotationClass.java.name }
    override val name = field.name!!
}

private data class JavaReflectionMethodAdapter(val method: Executable, val ownerClassName: String) : MethodAdapter {
    override val name = when (method) {
        is Method         -> method.name
        is Constructor<*> -> "<init>"
        else              -> TODO()
    }

    override val annotations = method.declaredAnnotations!!.map { it.annotationClass.java.name }

    override val parameters = run {
        val types = method.parameterTypes.orEmpty().map { cls ->
            when {
                cls.isArray -> tryOrDefault(cls.name) {
                    val componentTypes = cls.generateWhileNotNull { componentType }
                    componentTypes.last().name + "[]".repeat(componentTypes.size - 1)
                }
                else        -> cls.name
            }!!
        }
        val allAnnotations = method.parameterAnnotations.orEmpty().map { it.map { it.annotationClass.java.name } }
        mergeParameters(types, allAnnotations)
    }

    override val returnType = (method as? Method)?.returnType?.name.orEmpty()
    override val signature = "$ownerClassName.$name(${parameters.joinToString()})"
}

private fun mergeParameters(types: List<String>, allAnnotations: List<List<String>>): List<ParameterAdapter> =
        types.zipWithPadding(allAnnotations) { type, annotations ->
            CommonParameterAdapter(type!!, annotations.orEmpty())
        }

private data class CommonParameterAdapter(override val type: String, override val annotations: List<String>) :
        ParameterAdapter