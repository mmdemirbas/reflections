package org.reflections.adapters

import org.reflections.ReflectionUtils
import org.reflections.util.Utils
import org.reflections.vfs.Vfs
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

val JavaReflectionFactory = { file: Vfs.File ->
    JavaReflectionClassAdapter(ReflectionUtils.forName(file.relativePath!!.replace("/", ".").replace(".class", ""))!!)
}

data class JavaReflectionClassAdapter(val delegate: Class<*>) : ClassAdapter {
    override val fields get() = delegate.declaredFields.map { JavaReflectionFieldAdapter(it) }
    override val methods
        get() = (delegate.declaredMethods.asList() + delegate.declaredConstructors).map {
            JavaReflectionMethodAdapter(it)
        }

    override val annotations get() = delegate.declaredAnnotations.map { it.annotationClass.java.name }
    override val name get() = delegate.name!!
    override val superclass get() = delegate.superclass?.name.orEmpty()
    override val interfaces get() = delegate.interfaces?.map { it.name }.orEmpty()
    override val isPublic get() = Modifier.isPublic(delegate.modifiers)
}

data class JavaReflectionFieldAdapter(val delegate: Field) : FieldAdapter {
    override val annotations get() = delegate.declaredAnnotations.map { it.annotationClass.java.name }
    override val name get() = delegate.name!!
    override val isPublic get() = Modifier.isPublic(delegate.modifiers)
}

data class JavaReflectionMethodAdapter(val delegate: Member) : MethodAdapter {
    override val name
        get() = when (delegate) {
            is Method         -> delegate.getName()
            is Constructor<*> -> "<init>"
            else              -> null
        }

    override val parameters
        get() = (delegate as? Executable)?.parameterTypes?.map {
            when {
                it.isArray -> try {
                    var cl: Class<*> = it
                    var dim = 0
                    while (cl.isArray) {
                        dim++
                        cl = cl.componentType
                    }
                    cl.name + Utils.repeat("[]", dim)
                } catch (e: Throwable) {
                    it.name
                }
                else       -> it.name
            }
        }.orEmpty()

    override val annotations get() = (delegate as Executable).declaredAnnotations!!.map { it.annotationClass.java.name }

    override fun parameterAnnotations(parameterIndex: Int) =
            (delegate as Executable).parameterAnnotations[parameterIndex].map { it.annotationClass.java.name }

    override val returnType get() = (delegate as Method).returnType.name!!
    override val modifier get() = Modifier.toString(delegate.modifiers)!!
    override val isPublic get() = Modifier.isPublic(delegate.modifiers)
}