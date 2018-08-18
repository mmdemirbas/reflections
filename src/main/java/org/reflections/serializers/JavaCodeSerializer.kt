package org.reflections.serializers

import org.reflections.Multimap
import org.reflections.ReflectionUtils
import org.reflections.Reflections
import org.reflections.Reflections.Companion.log
import org.reflections.ReflectionsException
import org.reflections.scanners.TypeElementsScanner
import org.reflections.util.Utils
import org.reflections.util.Utils.index
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.charset.Charset
import java.nio.file.Files.write
import java.util.*

/**
 * Serialization of Reflections to java code
 *
 *  Serializes types and types elements into interfaces respectively to fully qualified name,
 *
 *  For example, after saving with JavaCodeSerializer:
 * <pre>
 * reflections.save(filename, new JavaCodeSerializer());
</pre> *
 *
 * Saved file should look like:
 * <pre>
 * public interface MyModel {
 * public interface my {
 * public interface package1 {
 * public interface MyClass1 {
 * public interface fields {
 * public interface f1 {}
 * public interface f2 {}
 * }
 * public interface methods {
 * public interface m1 {}
 * public interface m2 {}
 * }
 * ...
 * }
</pre> *
 *
 *  Use the different resolve methods to resolve the serialized element into Class, Field or Method. for example:
 * <pre>
 * Class m1Ref = MyModel.my.package1.MyClass1.methods.m1.class;
 * Method method = JavaCodeSerializer.resolve(m1Ref);
</pre> *
 *
 * The [.save] method filename should be in the pattern: path/path/path/package.package.classname
 *
 * depends on Reflections configured with [org.reflections.scanners.TypeElementsScanner]
 */
class JavaCodeSerializer : Serializer {

    override fun read(inputStream: InputStream): Reflections {
        throw UnsupportedOperationException("read is not implemented on JavaCodeSerializer")
    }

    /**
     * name should be in the pattern: path/path/path/package.package.classname,
     * for example <pre>/data/projects/my/src/main/java/org.my.project.MyStore</pre>
     * would create class MyStore in package org.my.project in the path /data/projects/my/src/main/java
     */
    override fun save(reflections: Reflections, name: String): File {
        var name = name
        if (name.endsWith("/")) {
            name = name.substring(0, name.length - 1) //trim / at the end
        }

        //prepare file
        val filename = name.replace('.', '/') + ".java"
        val file = Utils.prepareFile(filename)

        //get package and class names
        val packageName: String
        val className: String
        val lastDot = name.lastIndexOf('.')
        if (lastDot == -1) {
            packageName = ""
            className = name.substring(name.lastIndexOf('/') + 1)
        } else {
            packageName = name.substring(name.lastIndexOf('/') + 1, lastDot)
            className = name.substring(lastDot + 1)
        }

        //generate
        try {
            val sb = StringBuilder()
            sb.append("//generated using Reflections JavaCodeSerializer").append(" [").append(Date()).append(']')
                .append('\n')
            if (!packageName.isEmpty()) {
                sb.append("package ").append(packageName).append(";\n")
                sb.append('\n')
            }
            sb.append("public interface ").append(className).append(" {\n\n")
            sb.append(toString(reflections))
            sb.append("}\n")

            write(File(filename).toPath(), sb.toString().toByteArray(Charset.defaultCharset()))

        } catch (e: IOException) {
            throw RuntimeException()
        }

        return file
    }

    override fun toString(reflections: Reflections): String {
        if (reflections.store!!.get(index(TypeElementsScanner::class.java)).isEmpty) {
            log?.warn("JavaCodeSerializer needs TypeElementsScanner configured")
        }

        val sb = StringBuilder()

        var prevPaths: List<String> = listOf()
        var indent = 1

        val keys = reflections.store!!.get(index(TypeElementsScanner::class.java)).keySet().toMutableList()
        Collections.sort(keys)
        for (fqn in keys) {
            val typePaths = listOf(*fqn.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())

            //skip indention
            var i = 0
            while (i < Math.min(typePaths.size, prevPaths.size) && typePaths[i] == prevPaths[i]) {
                i++
            }

            //indent left
            for (j in prevPaths.size downTo i + 1) {
                sb.append(Utils.repeat("\t", --indent)).append("}\n")
            }

            //indent right - add packages
            for (j in i until typePaths.size - 1) {
                sb.append(Utils.repeat("\t", indent++)).append("public interface ")
                    .append(getNonDuplicateName(typePaths[j], typePaths, j)).append(" {\n")
            }

            //indent right - add class
            val className = typePaths[typePaths.size - 1]

            //get fields and methods
            val annotations = mutableListOf<String>()
            val fields = mutableListOf<String>()
            val methods = Multimap<String, String>()

            for (element in reflections.store.get(index(TypeElementsScanner::class.java), fqn)) {
                if (element.startsWith("@")) {
                    annotations.add(element.substring(1))
                } else if (element.contains("(")) {
                    //method
                    if (!element.startsWith("<")) {
                        val i1 = element.indexOf('(')
                        val name = element.substring(0, i1)
                        val params = element.substring(i1 + 1, element.indexOf(')'))

                        var paramsDescriptor = ""
                        if (!params.isEmpty()) {
                            paramsDescriptor = tokenSeparator +
                                    params.replace(dotSeparator, tokenSeparator).replace(", ", doubleSeparator).replace(
                                            "[]",
                                            arrayDescriptor)
                        }
                        val normalized = name + paramsDescriptor
                        methods.put(name, normalized)
                    }
                } else if (!Utils.isEmpty(element)) {
                    //field
                    fields.add(element)
                }
            }

            //add class and it's fields and methods
            sb.append(Utils.repeat("\t", indent++)).append("public interface ")
                .append(getNonDuplicateName(className, typePaths, typePaths.size - 1)).append(" {\n")

            //add fields
            if (!fields.isEmpty()) {
                sb.append(Utils.repeat("\t", indent++)).append("public interface fields {\n")
                for (field in fields) {
                    sb.append(Utils.repeat("\t", indent)).append("public interface ")
                        .append(getNonDuplicateName(field, typePaths)).append(" {}\n")
                }
                sb.append(Utils.repeat("\t", --indent)).append("}\n")
            }

            //add methods
            if (!methods.isEmpty) {
                sb.append(Utils.repeat("\t", indent++)).append("public interface methods {\n")
                for ((simpleName, normalized) in methods.entries()) {

                    var methodName = if (methods.get(simpleName)!!.size == 1) simpleName else normalized

                    methodName = getNonDuplicateName(methodName, fields)

                    sb.append(Utils.repeat("\t", indent)).append("public interface ")
                        .append(getNonDuplicateName(methodName, typePaths)).append(" {}\n")
                }
                sb.append(Utils.repeat("\t", --indent)).append("}\n")
            }

            //add annotations
            if (!annotations.isEmpty()) {
                sb.append(Utils.repeat("\t", indent++)).append("public interface annotations {\n")
                for (annotation in annotations) {
                    var nonDuplicateName = annotation
                    nonDuplicateName = getNonDuplicateName(nonDuplicateName, typePaths)
                    sb.append(Utils.repeat("\t", indent)).append("public interface ").append(nonDuplicateName)
                        .append(" {}\n")
                }
                sb.append(Utils.repeat("\t", --indent)).append("}\n")
            }

            prevPaths = typePaths
        }


        //close indention
        for (j in prevPaths.size downTo 1) {
            sb.append(Utils.repeat("\t", j)).append("}\n")
        }

        return sb.toString()
    }

    companion object {

        private val pathSeparator = "_"
        private val doubleSeparator = "__"
        private val dotSeparator = "."
        private val arrayDescriptor = "$$"
        private val tokenSeparator = "_"

        private fun getNonDuplicateName(candidate: String, prev: List<String>, offset: Int = prev.size): String {
            val normalized = normalize(candidate)
            for (i in 0 until offset) {
                if (normalized == prev[i]) {
                    return getNonDuplicateName(normalized + tokenSeparator, prev, offset)
                }
            }

            return normalized
        }

        private fun normalize(candidate: String): String {
            return candidate.replace(dotSeparator, pathSeparator)
        }

        //
        @Throws(ClassNotFoundException::class)
        fun resolveClassOf(element: Class<*>): Class<*> {
            var cursor: Class<*>? = element
            val ognl = mutableListOf<String>()

            while (cursor != null) {
                ognl.add(cursor.simpleName)
                cursor = cursor.declaringClass
            }

            val classOgnl = ognl.reversed().subList(1, ognl.size).joinToString(".").replace(".$", "$")
            return Class.forName(classOgnl)
        }

        fun resolveClass(aClass: Class<*>): Class<*> {
            try {
                return resolveClassOf(aClass)
            } catch (e: Exception) {
                throw ReflectionsException("could not resolve to class " + aClass.name, e)
            }

        }

        fun resolveField(aField: Class<*>): Field {
            try {
                val name = aField.simpleName
                val declaringClass = aField.declaringClass.declaringClass
                return resolveClassOf(declaringClass).getDeclaredField(name)
            } catch (e: Exception) {
                throw ReflectionsException("could not resolve to field " + aField.name, e)
            }

        }

        fun resolveAnnotation(annotation: Class<*>): Annotation {
            try {
                val name = annotation.simpleName.replace(pathSeparator, dotSeparator)
                val declaringClass = annotation.declaringClass.declaringClass
                val aClass = resolveClassOf(declaringClass)
                val aClass1 = ReflectionUtils.forName(name) as Class<out Annotation>
                return aClass.getAnnotation(aClass1)
            } catch (e: Exception) {
                throw ReflectionsException("could not resolve to annotation " + annotation.name, e)
            }

        }

        fun resolveMethod(aMethod: Class<*>): Method {
            val methodOgnl = aMethod.simpleName

            try {
                val methodName: String
                val paramTypes: Array<Class<*>?>
                if (methodOgnl.contains(tokenSeparator)) {
                    methodName = methodOgnl.substring(0, methodOgnl.indexOf(tokenSeparator))
                    val params =
                            methodOgnl.substring(methodOgnl.indexOf(tokenSeparator) + 1)
                                .split(doubleSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    paramTypes = params.indices.map { i ->
                        val typeName = params[i].replace(arrayDescriptor, "[]").replace(pathSeparator, dotSeparator)
                        ReflectionUtils.forName(typeName)
                    }.toTypedArray()
                } else {
                    methodName = methodOgnl
                    paramTypes = emptyArray()
                }

                val declaringClass = aMethod.declaringClass.declaringClass
                return resolveClassOf(declaringClass).getDeclaredMethod(methodName, *paramTypes)
            } catch (e: Exception) {
                throw ReflectionsException("could not resolve to method " + aMethod.name, e)
            }

        }
    }
}
