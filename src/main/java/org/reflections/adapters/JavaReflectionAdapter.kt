package org.reflections.adapters

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

class JavaReflectionAdapter : MetadataAdapter<JavaReflectionClassWrapper, JavaReflectionFieldWrapper, JavaReflectionMethodWrapper> {

    override fun getFields(cls: JavaReflectionClassWrapper) =
            cls.delegate.declaredFields.map { JavaReflectionFieldWrapper(it) }

    override fun getMethods(cls: JavaReflectionClassWrapper) =
            (cls.delegate.declaredMethods.asList() + cls.delegate.declaredConstructors).map {
                JavaReflectionMethodWrapper(it)
            }

    override fun getMethodName(method: JavaReflectionMethodWrapper) = when {
        method.delegate is Method         -> method.delegate.getName()
        method.delegate is Constructor<*> -> "<init>"
        else                              -> null
    }

    override fun getParameterNames(member: JavaReflectionMethodWrapper) =
            (member.delegate as? Executable)?.parameterTypes?.map { getName(it) }.orEmpty()

    override fun getClassAnnotationNames(aClass: JavaReflectionClassWrapper) =
            aClass.delegate.declaredAnnotations.map { it.annotationClass.java.name }

    override fun getFieldAnnotationNames(field: JavaReflectionFieldWrapper) =
            field.delegate.declaredAnnotations.map { it.annotationClass.java.name }

    override fun getMethodAnnotationNames(method: JavaReflectionMethodWrapper) =
            (method.delegate as Executable).declaredAnnotations!!.map { it.annotationClass.java.name }

    override fun getParameterAnnotationNames(method: JavaReflectionMethodWrapper, parameterIndex: Int) =
            (method.delegate as Executable).parameterAnnotations[parameterIndex].map { it.annotationClass.java.name }

    override fun getReturnTypeName(method: JavaReflectionMethodWrapper) = (method as Method).returnType.name!!

    override fun getFieldName(field: JavaReflectionFieldWrapper) = field.delegate.name!!

    override fun getOrCreateClassObject(file: File) =
            forName(file.relativePath!!.replace("/", ".").replace(".class", ""))?.let { JavaReflectionClassWrapper(it) }

    override fun getMethodModifier(method: JavaReflectionMethodWrapper) = Modifier.toString(method.delegate.modifiers)!!

    override fun getMethodKey(cls: JavaReflectionClassWrapper, method: JavaReflectionMethodWrapper) =
            getMethodName(method) + '('.toString() + getParameterNames(method).joinToString() + ')'.toString()

    override fun getMethodFullKey(cls: JavaReflectionClassWrapper, method: JavaReflectionMethodWrapper) =
            getClassName(cls) + '.'.toString() + getMethodKey(cls, method)

    override fun isPublic(o: Any?): Boolean {
        val mod =
                (o as? JavaReflectionClassWrapper)?.delegate?.modifiers
                ?: (o as? JavaReflectionMethodWrapper)?.delegate?.modifiers

        return mod != null && Modifier.isPublic(mod)
    }

    override fun getClassName(cls: JavaReflectionClassWrapper) = cls.delegate.name!!

    override fun getSuperclassName(cls: JavaReflectionClassWrapper) = cls.delegate.superclass?.name.orEmpty()

    override fun getInterfacesNames(cls: JavaReflectionClassWrapper) =
            cls.delegate.interfaces?.map { it.name }.orEmpty()

    override fun acceptsInput(file: String) = file.endsWith(".class")

    private fun getName(type: Class<*>): String {
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
            }
        }
        return type.name
    }
}
