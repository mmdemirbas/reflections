package com.mmdemirbas.reflections

import javassist.bytecode.*
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.lang.reflect.*
import kotlin.math.max

val createClassAdapter = { virtualFile: VirtualFile ->
    // the Javassist is preferred in terms of performance and class loading.
    try {
        virtualFile.openInputStream()
            .use { stream -> ClassFile(DataInputStream(BufferedInputStream(stream))).asAdapter() }
    } catch (e: Exception) {
        try {
            val relativePath = virtualFile.relativePath.throwIfNull("relativePath of virtualFile: $virtualFile")
            val typeName = relativePath.replace("/", ".").replace(".class", "")
            classForName(typeName).throwIfNull("classForName($typeName)").asAdapter().also {
                logger.debug("Using Java Reflection, loaded ${virtualFile.name}")
            }
        } catch (e2: Exception) {
            e2.addSuppressed(e)
            throw e2
        }
    }
}


// data classes ////////////////////////////////////////////////////////////////////////////////////////////////////////

data class ClassAdapter(val name: String,
                        val annotations: List<String>,
                        val fields: List<FieldAdapter>,
                        val methods: List<MethodAdapter>,
                        val superclass: String,
                        val interfaces: List<String>)

data class FieldAdapter(val name: String, val annotations: List<String>)

data class MethodAdapter(val name: String,
                         val annotations: List<String>,
                         val params: List<ParamAdapter>,
                         val returnType: String,
                         val signature: String)

data class ParamAdapter(val name: String?, val type: String, val annotations: List<String>)


// factories for javassist /////////////////////////////////////////////////////////////////////////////////////////////

private fun ClassFile.asAdapter() = ClassAdapter(name = name!!,
                                                 annotations = listOf(AnnotationsAttribute.visibleTag,
                                                                      AnnotationsAttribute.invisibleTag).flatMap {
                                                     (getAttribute(it) as AnnotationsAttribute?)?.annotations.orEmpty()
                                                         .map { it.typeName }
                                                 },
                                                 fields = fields.map { (it as FieldInfo).asAdapter() },
                                                 methods = methods.map {
                                                     (it as MethodInfo).asAdapter(name)
                                                 },
                                                 superclass = superclass.throwIfNull("superclass of ClassFile: ${this}"),
                                                 interfaces = interfaces.map { it.throwIfNull("one of interfaces of ClassFile: ${this}") })

private fun FieldInfo.asAdapter() = FieldAdapter(name = name!!,
                                                 annotations = listOf(AnnotationsAttribute.visibleTag,
                                                                      AnnotationsAttribute.invisibleTag).flatMap {
                                                     (getAttribute(it) as AnnotationsAttribute?)?.annotations.orEmpty()
                                                         .map { it.typeName }
                                                 })

// todo: target'ı non-null, kendisi de platform tipi olan yerlerde name gibi null gelmesi beklenmiyorsa !! kullanmaya gerek yok.
fun MethodInfo.asAdapter(ownerClassName: String) = MethodAdapter(name = name!!,
                                                                 annotations = listOf(AnnotationsAttribute.visibleTag,
                                                                                      AnnotationsAttribute.invisibleTag).flatMap {
                                                                     (getAttribute(it) as AnnotationsAttribute?)?.annotations.orEmpty()
                                                                         .map { it.typeName }
                                                                 },
                                                                 params = params("$ownerClassName.$name"),
                                                                 returnType = descriptor.substringAfterLast(')').splitDescriptorToTypeNames()[0].throwIfNull(
                                                                         "returnType derived from descriptor: $descriptor")

                                                                 ,
                                                                 signature = "$ownerClassName.$name(${params("$ownerClassName.$name").joinToString { it.type }})")

private fun MethodInfo.params(methodName: String): List<ParamAdapter> {
    val table = codeAttribute?.getAttribute(LocalVariableAttribute.tag) as LocalVariableAttribute?
    val types = descriptor.substringBetween('(', ')').splitDescriptorToTypeNames()
    val allAnnotations =
            zipWithPadding(paramAnnotations(ParameterAnnotationsAttribute.visibleTag),
                           paramAnnotations(ParameterAnnotationsAttribute.invisibleTag)) { v, i -> v.orEmpty() + i.orEmpty() }

    val static = Modifier.isStatic(accessFlags)
    val offset = if (static) 0 else 1 //skip 'this'
    val count = max(types.size, allAnnotations.size)
    val paramNames = when (table) {
        null -> emptyList()
        else -> (0 until count).map { i -> table.variableName(offset + i) ?: "arg$i" }
    }
    return paramAdapters(methodName = methodName, names = paramNames, types = types, allAnnotations = allAnnotations)
}

private fun MethodInfo.paramAnnotations(tag: String) =
        (getAttribute(tag) as ParameterAnnotationsAttribute?)?.annotations.orEmpty().map { it.map { it.typeName } }

private fun String.splitDescriptorToTypeNames() = when {
    isEmpty() -> listOf()
    else      -> {
        val iterator = Descriptor.Iterator(this)
        val indices = whileNotNull { mapIf(iterator.hasNext()) { iterator.next() } }.toList() + length
        (0 until indices.size - 1).map { Descriptor.toString(substring(indices[it], indices[it + 1])) }
    }
}


// factories for java reflection ///////////////////////////////////////////////////////////////////////////////////////

private fun Class<*>.asAdapter() = ClassAdapter(name = name!!,
                                                fields = declaredFields.map { it.asAdapter() },
                                                methods = (declaredMethods.asList() + declaredConstructors).map { it.asAdapter() },
                                                annotations = declaredAnnotations.map { it.annotationClass.java.name },
                                                superclass = superclass?.name.orEmpty(),
                                                interfaces = interfaces.map { it.name!! })

private fun Field.asAdapter() =
        FieldAdapter(name = name!!, annotations = declaredAnnotations.map { it.annotationClass.java.name })

private fun Executable.asAdapter() = MethodAdapter(name = ctorAwareName,
                                                   annotations = declaredAnnotations.throwIfNull("declaredAnnotations of Executable: ${this}").map { it.annotationClass.java.name },
                                                   params = params(name),
                                                   returnType = (this as? Method)?.returnType?.name.orEmpty(),
                                                   signature = "${declaringClass.name}.$ctorAwareName(${params(name).joinToString { it.type }})")

private fun Executable.params(methodName: String) = paramAdapters(methodName = methodName,
                                                                  names = parameters.map { it.name },
                                                                  types = parameterTypes.orEmpty().map { cls ->
                                                                      when {
                                                                          cls.isArray -> tryOrDefault(cls.name!!) {
                                                                              val componentTypes =
                                                                                      cls.generateWhileNotNull { componentType }
                                                                              componentTypes.last().name + "[]".repeat(
                                                                                      componentTypes.size - 1)
                                                                          }
                                                                          else        -> cls.name!!
                                                                      }
                                                                  },
                                                                  allAnnotations = parameterAnnotations.orEmpty().map { it.map { it.annotationClass.java.name } })

private val Executable.ctorAwareName get() = if (this is Constructor<*>) "<init>" else name!!


// common //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private fun paramAdapters(methodName: String,
                          names: List<String?>,
                          types: List<String>,
                          allAnnotations: List<List<String>>) =
        zipWithPadding(names, types, allAnnotations) { name, type, annotations ->
            ParamAdapter(name = name,
                         type = type.throwIfNull("method: $methodName. param: name: $name, type: $type, annotations: $annotations"),
                         annotations = annotations.orEmpty())
        }
