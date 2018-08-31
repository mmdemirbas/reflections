package org.reflections.serializers

import org.reflections.scanners.SimpleScanner
import org.reflections.scanners.TypeElementsScanner
import org.reflections.serializers.JavaCodeSerializer.save
import org.reflections.util.Multimap
import org.reflections.util.classForName
import org.reflections.util.generateWhileNotNull
import org.reflections.util.makeParents
import org.reflections.util.substringBetween
import org.reflections.util.tryOrThrow
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files.write
import java.util.*

/**
 * Serialization of Reflections to java code
 *
 *  Serializes types and types elements into interfaces respectively to fully qualified name,
 *
 *  For example, after saving with JavaCodeSerializer:
 * ```
 * reflections.save(filename, new JavaCodeSerializer());
 * ```
 *
 * Saved file should look like:
 * ```
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
 * ```
 *
 *  Use the different resolve methods to resolve the serialized element into Class, Field or Method. for example:
 * ```
 * Class m1Ref = MyModel.my.package1.MyClass1.methods.m1.class;
 * Method method = JavaCodeSerializer.resolve(m1Ref);
 * ```
 *
 * The [save] method filename should be in the pattern: path/path/path/package.package.classname
 *
 * depends on Reflections configured with [org.reflections.scanners.TypeElementsScanner]
 */
object JavaCodeSerializer : Serializer {
    private const val pathSeparator = "_"
    private const val doubleSeparator = "__"
    private const val dotSeparator = "."
    private const val arrayDescriptor = "$$"
    private const val tokenSeparator = "_"

    override fun read(inputStream: InputStream) =
            throw UnsupportedOperationException("read is not implemented on JavaCodeSerializer")

    /**
     * name should be in the pattern: path/path/path/package.package.classname,
     * for example ```/data/projects/my/src/main/java/org.my.project.MyStore```
     * would create class MyStore in package org.my.project in the path /data/projects/my/src/main/java
     */
    override fun save(scanners: List<SimpleScanner<*>>, file: File) {
        var name = file.name
        if (name.endsWith("/")) {
            name = name.dropLast(1) //trim / at the end
        }

        //get package and class names
        val packageName: String
        val className: String
        when {
            name.contains('.') -> {
                packageName = name.substringBeforeLast('.').substringAfterLast('/')
                className = name.substringAfterLast('.')
            }
            else               -> {
                packageName = ""
                className = name.substringAfterLast('/')
            }
        }

        //generate
        val sb = StringBuilder()
        sb.append("//generated using Reflections JavaCodeSerializer").append(" [").append(Date()).append(']')
            .append('\n')
        if (!packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n")
            sb.append('\n')
        }
        sb.append("public interface ").append(className).append(" {\n\n")
        sb.append(toString(scanners))
        sb.append("}\n")

        write(File(name.replace('.', '/') + ".java").makeParents().toPath(),
              sb.toString().toByteArray(Charset.defaultCharset()))
    }

    override fun toString(scanners: List<SimpleScanner<*>>): String {

        val list = scanners.filterIsInstance<TypeElementsScanner>().flatMap { it.entries() }
        if (list.isEmpty()) throw RuntimeException("JavaCodeSerializer needs TypeElementsScanner configured")

        val sb = StringBuilder()
        var prevPaths: List<String> = listOf()
        var indent = 1

        list.sortedBy { it.key }.forEach { (fqn, values) ->
            val typePaths = fqn.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }

            //skip indention
            var i = 0
            while (i < Math.min(typePaths.size, prevPaths.size) && typePaths[i] == prevPaths[i]) {
                i++
            }

            //indent left
            (prevPaths.size downTo i + 1).forEach { j ->
                sb.append("\t".repeat(--indent)).append("}\n")
            }

            //indent right - add packages
            (i until typePaths.size - 1).forEach { j ->
                sb.append("\t".repeat(indent++)).append("public interface ")
                    .append(getNonDuplicateName(typePaths[j], typePaths, j)).append(" {\n")
            }

            //indent right - add class
            val className = typePaths[typePaths.size - 1]

            //get fields and methods
            val annotations = mutableListOf<String>()
            val fields = mutableListOf<String>()
            val methods = Multimap<String, String>()

            values.forEach { element ->
                when {
                    element.startsWith("@") -> annotations.add(element.substring(1))
                    element.contains("(")   -> //method
                        if (!element.startsWith("<")) {
                            val name = element.substringBefore('(')
                            val params = element.substringBetween('(', ')')
                            val normalized = when {
                                params.isEmpty() -> name
                                else             -> name + tokenSeparator + params.replace(dotSeparator,
                                                                                           tokenSeparator).replace(", ",
                                                                                                                   doubleSeparator).replace(
                                        "[]",
                                        arrayDescriptor)
                            }
                            methods.put(name, normalized)
                        }
                    !element.isEmpty()      -> //field
                        fields.add(element)
                }
            }

            //add class and it's fields and methods
            sb.append("\t".repeat(indent++)).append("public interface ")
                .append(getNonDuplicateName(className, typePaths, typePaths.size - 1)).append(" {\n")

            //add fields
            if (!fields.isEmpty()) {
                sb.append("\t".repeat(indent++)).append("public interface fields {\n")
                fields.forEach { field ->
                    sb.append("\t".repeat(indent)).append("public interface ")
                        .append(getNonDuplicateName(field, typePaths)).append(" {}\n")
                }
                sb.append("\t".repeat(--indent)).append("}\n")
            }

            //add methods
            if (!methods.isEmpty()) {
                sb.append("\t".repeat(indent++)).append("public interface methods {\n")
                methods.entries().forEach { (simpleName, normalized) ->
                    val methodName = if (methods.get(simpleName)!!.size == 1) simpleName else normalized
                    sb.append("\t".repeat(indent)).append("public interface ")
                        .append(getNonDuplicateName(getNonDuplicateName(methodName, fields), typePaths)).append(" {}\n")
                }
                sb.append("\t".repeat(--indent)).append("}\n")
            }

            //add annotations
            if (!annotations.isEmpty()) {
                sb.append("\t".repeat(indent++)).append("public interface annotations {\n")
                annotations.forEach { annotation ->
                    sb.append("\t".repeat(indent)).append("public interface ")
                        .append(getNonDuplicateName(annotation, typePaths)).append(" {}\n")
                }
                sb.append("\t".repeat(--indent)).append("}\n")
            }

            prevPaths = typePaths
        }

        //close indention
        (prevPaths.size downTo 1).forEach { j ->
            sb.append("\t".repeat(j)).append("}\n")
        }

        return sb.toString()
    }

    private fun getNonDuplicateName(candidate: String, prev: List<String>, offset: Int = prev.size): String {
        val normalized = normalize(candidate)
        return if ((0 until offset).any { normalized == prev[it] }) getNonDuplicateName(normalized + tokenSeparator,
                                                                                        prev,
                                                                                        offset) else normalized
    }

    private fun normalize(candidate: String) = candidate.replace(dotSeparator, pathSeparator)

    fun resolveClass(aClass: Class<*>) = tryOrThrow("could not resolve to class ${aClass.name}") {
        resolveClassOf(aClass)
    }

    fun resolveField(aField: Class<*>) = tryOrThrow("could not resolve to field ${aField.name}") {
        resolveClassOf(aField.declaringClass.declaringClass).getDeclaredField(aField.simpleName)!!
    }

    fun resolveAnnotation(annotation: Class<*>) = tryOrThrow("could not resolve to annotation ${annotation.name}") {
        resolveClassOf(annotation.declaringClass.declaringClass).getAnnotation(classForName(annotation.simpleName.replace(
                pathSeparator,
                dotSeparator)) as Class<out Annotation>)!!
    }

    fun resolveMethod(method: Class<*>) = tryOrThrow("could not resolve to method ${method.name}") {
        resolveClassOf(method.declaringClass.declaringClass).getDeclaredMethod(method.simpleName.substringBefore(
                tokenSeparator),
                                                                               *method.simpleName.substringAfter(
                                                                                       tokenSeparator,
                                                                                       missingDelimiterValue = "").split(
                                                                                       doubleSeparator.toRegex()).dropLastWhile { it.isEmpty() }.map { param ->
                                                                                   classForName(param.replace(
                                                                                           arrayDescriptor,
                                                                                           "[]").replace(pathSeparator,
                                                                                                         dotSeparator))
                                                                               }.toTypedArray())!!
    }

    fun resolveClassOf(element: Class<*>): Class<*> =
            Class.forName(element.generateWhileNotNull { declaringClass }.map { it.simpleName }.reversed().drop(1).joinToString(
                    ".").replace(".$", "$"))
}
