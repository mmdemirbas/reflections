package org.reflections.adapters

import com.google.common.base.Joiner
import com.google.common.collect.Lists
import org.reflections.JavaReflectionClassWrapper
import org.reflections.JavaReflectionFieldWrapper
import org.reflections.JavaReflectionMethodWrapper
import org.reflections.ReflectionUtils.forName
import org.reflections.util.Utils
import org.reflections.vfs.Vfs.File
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

/**  */
class JavaReflectionAdapter : MetadataAdapter<JavaReflectionClassWrapper, JavaReflectionFieldWrapper, JavaReflectionMethodWrapper> {

    override fun getFields(cls: JavaReflectionClassWrapper): List<JavaReflectionFieldWrapper> {
        return cls.delegate.declaredFields.map { JavaReflectionFieldWrapper(it) }
    }

    override fun getMethods(cls: JavaReflectionClassWrapper): List<JavaReflectionMethodWrapper> {
        val methods = Lists.newArrayList<JavaReflectionMethodWrapper>()
        methods.addAll(cls.delegate.declaredMethods.map { JavaReflectionMethodWrapper(it) })
        methods.addAll(cls.delegate.declaredConstructors.map { JavaReflectionMethodWrapper(it) })
        return methods
    }

    override fun getMethodName(method: JavaReflectionMethodWrapper): String? {
        return if (method.delegate is Method) method.delegate.getName() else if (method.delegate is Constructor<*>) "<init>" else null
    }

    override fun getParameterNames(member: JavaReflectionMethodWrapper): List<String> {
        val result = Lists.newArrayList<String>()

        val parameterTypes = (member as? Executable)?.parameterTypes

        if (parameterTypes != null) {
            for (paramType in parameterTypes) {
                val name = getName(paramType)
                result.add(name)
            }
        }

        return result
    }

    override fun getClassAnnotationNames(aClass: JavaReflectionClassWrapper): List<String> {
        return getAnnotationNames(aClass.delegate.declaredAnnotations)
    }

    override fun getFieldAnnotationNames(field: JavaReflectionFieldWrapper): List<String> {
        return getAnnotationNames(field.delegate.declaredAnnotations)
    }

    override fun getMethodAnnotationNames(method: JavaReflectionMethodWrapper): List<String> {
        val annotations = if (method.delegate is Executable) method.delegate.declaredAnnotations
        else null
        return getAnnotationNames(annotations!!)
    }

    override fun getParameterAnnotationNames(method: JavaReflectionMethodWrapper, parameterIndex: Int): List<String> {
        val annotations = if (method.delegate is Executable) method.delegate.parameterAnnotations
        else null

        return if (annotations != null) getAnnotationNames(annotations[parameterIndex]) else getAnnotationNames(null!!)
    }

    override fun getReturnTypeName(method: JavaReflectionMethodWrapper): String {
        return (method as Method).returnType.name
    }

    override fun getFieldName(field: JavaReflectionFieldWrapper): String {
        return field.delegate.name
    }

    override fun getOrCreateClassObject(file: File): JavaReflectionClassWrapper? {
        return getOrCreateClassObject(file, emptyList())
    }

    fun getOrCreateClassObject(file: File, loaders: List<ClassLoader>): JavaReflectionClassWrapper? {
        val name = file.relativePath!!.replace("/", ".").replace(".class", "")
        return forName(name, *loaders.toTypedArray())?.let { JavaReflectionClassWrapper(it) }
    }

    override fun getMethodModifier(method: JavaReflectionMethodWrapper): String {
        return Modifier.toString(method.delegate.modifiers)
    }

    override fun getMethodKey(cls: JavaReflectionClassWrapper, method: JavaReflectionMethodWrapper): String {
        return getMethodName(method) + '('.toString() + Joiner.on(", ").join(getParameterNames(method)) + ')'.toString()
    }

    override fun getMethodFullKey(cls: JavaReflectionClassWrapper, method: JavaReflectionMethodWrapper): String {
        return getClassName(cls) + '.'.toString() + getMethodKey(cls, method)
    }

    override fun isPublic(o: Any?): Boolean {
        val mod =
                (o as? JavaReflectionClassWrapper)?.delegate?.modifiers
                ?: (o as? JavaReflectionMethodWrapper)?.delegate?.modifiers

        return mod != null && Modifier.isPublic(mod)
    }

    override fun getClassName(cls: JavaReflectionClassWrapper): String {
        return cls.delegate.name
    }

    override fun getSuperclassName(cls: JavaReflectionClassWrapper): String {
        val superclass = cls.delegate.superclass
        return if (superclass != null) superclass.name else ""
    }

    override fun getInterfacesNames(cls: JavaReflectionClassWrapper): List<String> {
        val classes = cls.delegate.interfaces
        val names: MutableList<String>
        names = if (classes != null) ArrayList(classes.size) else ArrayList(0)
        if (classes != null) {
            for (cls1 in classes) {
                names.add(cls1.name)
            }
        }
        return names
    }

    override fun acceptsInput(file: String): Boolean {
        return file.endsWith(".class")
    }

    companion object {

        //
        private fun getAnnotationNames(annotations: Array<Annotation>): List<String> {
            val names = ArrayList<String>(annotations.size)
            for (annotation in annotations) {
                names.add(annotation.annotationClass.java.name)
            }
            return names
        }

        fun getName(type: Class<*>): String {
            if (type.isArray) {
                try {
                    var cl: Class<*> = type
                    var dim = 0
                    while (cl.isArray) {
                        dim++
                        cl = cl.componentType
                    }
                    return cl.name + Utils.repeat("[]", dim)
                } catch (e: Throwable) {
                    //
                }

            }
            return type.name
        }
    }
}
