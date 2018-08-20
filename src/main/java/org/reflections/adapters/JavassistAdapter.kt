package org.reflections.adapters

import javassist.bytecode.AccessFlag
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.ClassFile
import javassist.bytecode.Descriptor
import javassist.bytecode.Descriptor.Iterator
import javassist.bytecode.FieldInfo
import javassist.bytecode.MethodInfo
import javassist.bytecode.ParameterAnnotationsAttribute
import org.reflections.JavassistClassWrapper
import org.reflections.JavassistFieldWrapper
import org.reflections.JavassistMethodWrapper
import org.reflections.ReflectionsException
import org.reflections.vfs.Vfs
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException

/**
 *
 */
class JavassistAdapter : MetadataAdapter<JavassistClassWrapper, JavassistFieldWrapper, JavassistMethodWrapper> {

    override fun getFields(cls: JavassistClassWrapper) =
            cls.delegate.fields.map { JavassistFieldWrapper(it as FieldInfo) }

    override fun getMethods(cls: JavassistClassWrapper) =
            cls.delegate.methods.map { JavassistMethodWrapper(it as MethodInfo) }

    override fun getMethodName(method: JavassistMethodWrapper) = method.delegate.name!!

    override fun getParameterNames(method: JavassistMethodWrapper) =
            method.delegate.descriptor.substringBetween('(', ')').splitDescriptorToTypeNames()

    override fun getClassAnnotationNames(aClass: JavassistClassWrapper) =
            listOf(aClass.delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?,
                   aClass.delegate.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?).flatMap {
                it?.annotations.orEmpty().map { it.typeName }
            }

    override fun getFieldAnnotationNames(field: JavassistFieldWrapper) =
            listOf(field.delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?,
                   field.delegate.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?).flatMap {
                it?.annotations.orEmpty().map { it.typeName }
            }

    override fun getMethodAnnotationNames(method: JavassistMethodWrapper) =
            listOf(method.delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?,
                   method.delegate.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?).flatMap {
                it?.annotations.orEmpty().map { it.typeName }
            }

    override fun getParameterAnnotationNames(method: JavassistMethodWrapper, parameterIndex: Int) =
            listOf(method.delegate.getAttribute(ParameterAnnotationsAttribute.visibleTag) as ParameterAnnotationsAttribute?,
                   method.delegate.getAttribute(ParameterAnnotationsAttribute.invisibleTag) as ParameterAnnotationsAttribute?).flatMap {
                it?.annotations?.getOrNull(parameterIndex).orEmpty().map { it.typeName }
            }

    override fun getReturnTypeName(method: JavassistMethodWrapper) =
            method.delegate.descriptor.substringAfterLast(')').splitDescriptorToTypeNames()[0]!!

    override fun getFieldName(field: JavassistFieldWrapper) = field.delegate.name!!

    override fun getOrCreateClassObject(file: Vfs.File) = try {
        file.openInputStream().use { stream ->
            JavassistClassWrapper(ClassFile(DataInputStream(BufferedInputStream(stream))))
        }
    } catch (e: IOException) {
        throw ReflectionsException("could not create class file from " + file.name, e)
    }

    override fun getMethodModifier(method: JavassistMethodWrapper) = when {
        AccessFlag.isPrivate(method.delegate.accessFlags)   -> "private"
        AccessFlag.isProtected(method.delegate.accessFlags) -> "protected"
        AccessFlag.isPublic(method.delegate.accessFlags)    -> "public"
        else                                                -> ""
    }

    override fun getMethodKey(cls: JavassistClassWrapper, method: JavassistMethodWrapper) =
            getMethodName(method) + '('.toString() + getParameterNames(method).joinToString() + ')'.toString()

    override fun getMethodFullKey(cls: JavassistClassWrapper, method: JavassistMethodWrapper) =
            getClassName(cls) + '.'.toString() + getMethodKey(cls, method)

    override fun isPublic(o: Any?): Boolean {
        val accessFlags =
                (o as? JavassistClassWrapper)?.delegate?.accessFlags
                ?: ((o as? JavassistFieldWrapper)?.delegate?.accessFlags
                    ?: (o as? JavassistMethodWrapper)?.delegate?.accessFlags)

        return accessFlags != null && AccessFlag.isPublic(accessFlags)
    }

    override fun getClassName(cls: JavassistClassWrapper) = cls.delegate.name!!

    override fun getSuperclassName(cls: JavassistClassWrapper) = cls.delegate.superclass!!

    override fun getInterfacesNames(cls: JavassistClassWrapper) = cls.delegate.interfaces.map { it!! }

    override fun acceptsInput(file: String) = file.endsWith(".class")

    private fun String.splitDescriptorToTypeNames() = when {
        isEmpty() -> listOf()
        else      -> {
            val indices = mutableListOf<Int>()
            val iterator = Iterator(this)
            while (iterator.hasNext()) {
                indices.add(iterator.next())
            }
            indices.add(length)
            (0 until indices.size - 1).map {
                Descriptor.toString(substring(indices[it], indices[it + 1]))
            }
        }
    }

    private fun String.substringBetween(first: Char, last: Char): String =
            substring(indexOf(first) + 1, lastIndexOf(last))
}
