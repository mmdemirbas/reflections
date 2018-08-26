package org.reflections.adapters

import org.reflections.util.classForName
import org.reflections.util.generateWhileNotNull
import org.reflections.util.tryOrDefault
import org.reflections.vfs.VfsFile
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

val CreateJavaReflectionClassAdapter = { vfsFile: VfsFile ->
    JavaReflectionClassAdapter(classForName(vfsFile.relativePath!!.replace("/", ".").replace(".class", ""))!!)
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