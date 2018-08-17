package org.reflections.adapters

import com.google.common.base.Joiner
import com.google.common.collect.Lists
import javassist.bytecode.*
import javassist.bytecode.AccessFlag.isPrivate
import javassist.bytecode.AccessFlag.isProtected
import javassist.bytecode.Descriptor.Iterator
import javassist.bytecode.annotation.Annotation
import org.reflections.JavassistClassWrapper
import org.reflections.JavassistFieldWrapper
import org.reflections.JavassistMethodWrapper
import org.reflections.ReflectionsException
import org.reflections.util.Utils
import org.reflections.vfs.Vfs
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 *
 */
class JavassistAdapter : MetadataAdapter<JavassistClassWrapper, JavassistFieldWrapper, JavassistMethodWrapper> {

    override fun getFields(cls: JavassistClassWrapper): List<JavassistFieldWrapper> {
        return cls.delegate.fields.map { JavassistFieldWrapper(it as FieldInfo) }
    }

    override fun getMethods(cls: JavassistClassWrapper): List<JavassistMethodWrapper> {
        return cls.delegate.methods.map { JavassistMethodWrapper(it as MethodInfo) }
    }

    override fun getMethodName(method: JavassistMethodWrapper): String {
        return method.delegate.name
    }

    override fun getParameterNames(method: JavassistMethodWrapper): List<String> {
        var descriptor = method.delegate.descriptor
        descriptor = descriptor.substring(descriptor.indexOf('(') + 1, descriptor.lastIndexOf(')'))
        return splitDescriptorToTypeNames(descriptor)
    }

    override fun getClassAnnotationNames(aClass: JavassistClassWrapper): List<String> {
        return if (includeInvisibleTag) getAnnotationNames(aClass.delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?,
                                                           aClass.delegate.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?)
        else getAnnotationNames(aClass.delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute)
    }

    override fun getFieldAnnotationNames(field: JavassistFieldWrapper): List<String> {
        return if (includeInvisibleTag) getAnnotationNames(field.delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?,
                                                           field.delegate.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?)
        else getAnnotationNames(field.delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute)
    }

    override fun getMethodAnnotationNames(method: JavassistMethodWrapper): List<String> {
        return if (includeInvisibleTag) getAnnotationNames(method.delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?,
                                                           method.delegate.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?)
        else getAnnotationNames(method.delegate.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute)
    }

    override fun getParameterAnnotationNames(method: JavassistMethodWrapper, parameterIndex: Int): List<String> {
        val result = Lists.newArrayList<String>()

        val parameterAnnotationsAttributes =
                Lists.newArrayList(method.delegate.getAttribute(ParameterAnnotationsAttribute.visibleTag) as ParameterAnnotationsAttribute?,
                                   method.delegate.getAttribute(ParameterAnnotationsAttribute.invisibleTag) as ParameterAnnotationsAttribute?)

        if (parameterAnnotationsAttributes != null) {
            for (parameterAnnotationsAttribute in parameterAnnotationsAttributes) {
                if (parameterAnnotationsAttribute != null) {
                    val annotations = parameterAnnotationsAttribute.annotations
                    if (parameterIndex < annotations.size) {
                        val annotation = annotations[parameterIndex]
                        result.addAll(getAnnotationNames(annotation))
                    }
                }
            }
        }

        return result
    }

    override fun getReturnTypeName(method: JavassistMethodWrapper): String {
        var descriptor = method.delegate.descriptor
        descriptor = descriptor.substring(descriptor.lastIndexOf(')') + 1)
        return splitDescriptorToTypeNames(descriptor)[0]
    }

    override fun getFieldName(field: JavassistFieldWrapper): String {
        return field.delegate.name
    }

    override fun getOrCreateClassObject(file: Vfs.File): JavassistClassWrapper {
        var inputStream: InputStream? = null
        try {
            inputStream = file.openInputStream()
            val dis = DataInputStream(BufferedInputStream(inputStream))
            return JavassistClassWrapper(ClassFile(dis))
        } catch (e: IOException) {
            throw ReflectionsException("could not create class file from " + file.name, e)
        } finally {
            Utils.close(inputStream)
        }
    }

    override fun getMethodModifier(method: JavassistMethodWrapper): String {
        val accessFlags = method.delegate.accessFlags
        return if (isPrivate(accessFlags)) "private"
        else if (isProtected(accessFlags)) "protected" else if (isPublic(accessFlags)) "public" else ""
    }

    override fun getMethodKey(cls: JavassistClassWrapper, method: JavassistMethodWrapper): String {
        return getMethodName(method) + '('.toString() + Joiner.on(", ").join(getParameterNames(method)) + ')'.toString()
    }

    override fun getMethodFullKey(cls: JavassistClassWrapper, method: JavassistMethodWrapper): String {
        return getClassName(cls) + '.'.toString() + getMethodKey(cls, method)
    }

    override fun isPublic(o: Any?): Boolean {
        val accessFlags =
                (o as? JavassistClassWrapper)?.delegate?.accessFlags
                ?: ((o as? JavassistFieldWrapper)?.delegate?.accessFlags
                    ?: (o as? JavassistMethodWrapper)?.delegate?.accessFlags)

        return accessFlags != null && AccessFlag.isPublic(accessFlags)
    }

    //
    override fun getClassName(cls: JavassistClassWrapper): String {
        return cls.delegate.name
    }

    override fun getSuperclassName(cls: JavassistClassWrapper): String {
        return cls.delegate.superclass
    }

    override fun getInterfacesNames(cls: JavassistClassWrapper): List<String> {
        return Arrays.asList(*cls.delegate.interfaces)
    }

    override fun acceptsInput(file: String): Boolean {
        return file.endsWith(".class")
    }

    companion object {

        /**
         * setting this to false will result in returning only visible annotations from the relevant methods here (only [java.lang.annotation.RetentionPolicy.RUNTIME])
         */
        val includeInvisibleTag = true

        //
        private fun getAnnotationNames(vararg annotationsAttributes: AnnotationsAttribute?): List<String> {
            val result = Lists.newArrayList<String>()

            if (annotationsAttributes != null) {
                for (annotationsAttribute in annotationsAttributes) {
                    if (annotationsAttribute != null) {
                        for (annotation in annotationsAttribute.annotations) {
                            result.add(annotation.typeName)
                        }
                    }
                }
            }

            return result
        }

        private fun getAnnotationNames(annotations: Array<Annotation>): List<String> {
            val result = Lists.newArrayList<String>()

            for (annotation in annotations) {
                result.add(annotation.typeName)
            }

            return result
        }

        private fun splitDescriptorToTypeNames(descriptors: String?): List<String> {
            val result = Lists.newArrayList<String>()

            if (descriptors != null && !descriptors.isEmpty()) {

                val indices = Lists.newArrayList<Int>()
                val iterator = Iterator(descriptors)
                while (iterator.hasNext()) {
                    indices.add(iterator.next())
                }
                indices.add(descriptors.length)

                for (i in 0 until indices.size - 1) {
                    val s1 = Descriptor.toString(descriptors.substring(indices[i], indices[i + 1]))
                    result.add(s1)
                }

            }

            return result
        }
    }
}
