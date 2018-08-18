package org.reflections.util

import org.reflections.ReflectionUtils.forName
import org.reflections.Reflections
import org.reflections.ReflectionsException
import org.reflections.scanners.Scanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.*

/**
 * a garbage can of convenient methods
 */
object Utils {

    @JvmStatic
    fun repeat(string: String, times: Int): String {
        val sb = StringBuilder()

        for (i in 0 until times) {
            sb.append(string)
        }

        return sb.toString()
    }

    /**
     * isEmpty compatible with Java 5
     */
    @JvmStatic
    fun isEmpty(s: String?): Boolean {
        return s == null || s.isEmpty()
    }

    @JvmStatic
    fun isEmpty(objects: Array<*>?): Boolean {
        return objects == null || objects.size == 0
    }

    @JvmStatic
    fun prepareFile(filename: String): File {
        val file = File(filename)
        val parent = file.absoluteFile.parentFile
        if (!parent.exists()) {

            parent.mkdirs()
        }
        return file
    }

    @Throws(ReflectionsException::class)
    @JvmStatic
    fun getMemberFromDescriptor(descriptor: String, vararg classLoaders: ClassLoader): Member {
        val p0 = descriptor.lastIndexOf('(')
        val memberKey = if (p0 == -1) descriptor else descriptor.substring(0, p0)
        val methodParameters = if (p0 == -1) "" else descriptor.substring(p0 + 1, descriptor.lastIndexOf(')'))

        val p1 = Math.max(memberKey.lastIndexOf('.'), memberKey.lastIndexOf('$'))
        val className = memberKey.substring(memberKey.lastIndexOf(' ') + 1, p1)
        val memberName = memberKey.substring(p1 + 1)

        val parameterTypes: Array<Class<*>>
        if (!isEmpty(methodParameters)) {
            val parameterNames = methodParameters.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val result = ArrayList<Class<*>>(parameterNames.size)
            for (name in parameterNames) {
                val element = forName(name.trim { it <= ' ' }, *classLoaders)
                if (element != null) {
                    result.add(element)
                }
            }
            parameterTypes = result.toTypedArray()
        } else parameterTypes = emptyArray()

        var aClass = forName(className, *classLoaders)
        while (aClass != null) {
            try {
                return if (!descriptor.contains("(")) {
                    if (aClass.isInterface) aClass.getField(memberName) else aClass.getDeclaredField(memberName)
                } else if (isConstructor(descriptor)) {
                    if (aClass.isInterface) aClass.getConstructor(*parameterTypes)
                    else aClass.getDeclaredConstructor(*parameterTypes)
                } else {
                    if (aClass.isInterface) aClass.getMethod(memberName, *parameterTypes)
                    else aClass.getDeclaredMethod(memberName, *parameterTypes)
                }
            } catch (e: Exception) {
                aClass = aClass.superclass
            }

        }
        throw ReflectionsException("Can't resolve member named $memberName for class $className")
    }

    @JvmStatic
    fun getMethodsFromDescriptors(annotatedWith: Iterable<String>, vararg classLoaders: ClassLoader): Set<Method> {
        val result = mutableSetOf<Method>()
        for (annotated in annotatedWith) {
            if (!isConstructor(annotated)) {
                val member = getMemberFromDescriptor(annotated, *classLoaders) as Method
                if (member != null) {
                    result.add(member)
                }
            }
        }
        return result
    }

    @JvmStatic
    fun getConstructorsFromDescriptors(annotatedWith: Iterable<String>,
                                       vararg classLoaders: ClassLoader): Set<Constructor<*>> {
        val result = mutableSetOf<Constructor<*>>()
        for (annotated in annotatedWith) {
            if (isConstructor(annotated)) {
                val member = getMemberFromDescriptor(annotated, *classLoaders) as Constructor<*>
                if (member != null) {
                    result.add(member)
                }
            }
        }
        return result
    }

    @JvmStatic
    fun getMembersFromDescriptors(values: Iterable<String>, vararg classLoaders: ClassLoader): Set<Member> {
        val result = mutableSetOf<Member>()
        for (value in values) {
            try {
                result.add(getMemberFromDescriptor(value, *classLoaders))
            } catch (e: ReflectionsException) {
                throw ReflectionsException("Can't resolve member named $value", e)
            }

        }
        return result
    }

    @JvmStatic
    fun getFieldFromString(field: String, vararg classLoaders: ClassLoader): Field {
        val className = field.substring(0, field.lastIndexOf('.'))
        val fieldName = field.substring(field.lastIndexOf('.') + 1)

        try {
            return forName(className, *classLoaders)!!.getDeclaredField(fieldName)
        } catch (e: NoSuchFieldException) {
            throw ReflectionsException("Can't resolve field named $fieldName", e)
        }

    }

    @JvmStatic
    fun close(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: IOException) {
            if (Reflections.log != null) {
                Reflections.log.warn("Could not close InputStream", e)
            }
        }

    }

    @JvmStatic
    fun findLogger(aClass: Class<*>): Logger? {
        try {
            // This is to check whether an optional SLF4J binding is available. While SLF4J recommends that libraries
            // "should not declare a dependency on any SLF4J binding but only depend on slf4j-api", doing so forces
            // users of the library to either add a binding to the classpath (even if just slf4j-nop) or to set the
            // "slf4j.suppressInitError" system property in order to avoid the warning, which both is inconvenient.
            Class.forName("org.slf4j.impl.StaticLoggerBinder")
            return LoggerFactory.getLogger(aClass)
        } catch (e: Throwable) {
            return null
        }

    }

    @JvmStatic
    fun isConstructor(fqn: String): Boolean {
        return fqn.contains("init>")
    }

    @JvmStatic
    fun name(type: Class<*>): String {
        var type = type
        if (type.isArray) {
            var dim = 0
            while (type.isArray) {
                dim++
                type = type.componentType
            }
            return type.name + repeat("[]", dim)
        } else {
            return type.name
        }
    }

    @JvmStatic
    fun names(types: Iterable<Class<*>>): List<String> {
        val result = ArrayList<String>()
        for (type in types) {
            result.add(name(type))
        }
        return result
    }

    @JvmStatic
    fun names(vararg types: Class<*>): List<String> {
        return names(Arrays.asList(*types))
    }

    @JvmStatic
    fun name(constructor: Constructor<*>): String {
        return constructor.name + '.'.toString() + "<init>" + '('.toString() + names(*constructor.parameterTypes).joinToString() + ')'.toString()
    }

    @JvmStatic
    fun name(method: Method): String {
        return (method.declaringClass.name + '.'.toString() + method.name + '('.toString() + names(*method.parameterTypes).joinToString() + ')'.toString())
    }

    @JvmStatic
    fun name(field: Field): String {
        return field.declaringClass.name + '.'.toString() + field.name
    }

    @JvmStatic
    fun index(scannerClass: Class<out Scanner>): String {
        return scannerClass.simpleName
    }
}
