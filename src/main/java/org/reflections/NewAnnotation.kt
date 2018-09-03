package org.reflections

import javassist.util.proxy.ProxyFactory
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.KClass

/**
 * Creates an instance of this annotation type.
 */
fun <T : kotlin.Annotation> KClass<T>.newAnnotation(vararg properties: Pair<String, Any>) =
        java.newAnnotation(*properties)

/**
 * Creates an instance of this annotation type.
 */
fun <T : kotlin.Annotation> Class<T>.newAnnotation(vararg properties: Pair<String, Any>): T {
    val factory = ProxyFactory()
    factory.interfaces = arrayOf(this)
    factory.setHandler(annotationHandler(this, properties.toMap()))
    return (factory.createClass() as Class<out T>).newInstance()!!
}

fun <T : Annotation> annotationHandler(type: Class<T>,
                                       givenProperties: Map<String, Any>): (Any?, Method, Method?, Array<out Any?>) -> Any? {
    val propsAndNulls = type.declaredMethods.map { it.name!! to (givenProperties[it.name] ?: it.defaultValue) }
    val missingProps = propsAndNulls.filter { it.second == null }.map { it.first }
    if (missingProps.isNotEmpty()) throw IllegalArgumentException("Missing properties: $missingProps")
    val props = propsAndNulls as List<Pair<String, Any>>

    return handler@{ self: Any?, thisMethod: Method, proceed: Method?, args: Array<out Any?> ->
        val method = thisMethod.name
        val params = thisMethod.parameterCount
        when {
            params == 1 && method == "equals" -> {
                val arg = args.single()
                (self === arg) || (arg !== null && type.isInstance(arg) && props.all { (name, value) ->
                    val otherValue = arg.javaClass.getMethod(name).invoke(arg)
                    when (value) {
                        is Array<*> -> Arrays.equals(value, otherValue as? Array<*>)
                        else        -> value == otherValue
                    }.also {
                        if (it == false) {
                            println("this.$name  = $value")
                            println("other.$name = $otherValue")
                            println()
                        }
                    }
                })
            }
            params > 0                        -> throw NoSuchMethodException(thisMethod.toGenericString())
            method == "hashCode"              -> props.sumBy { (name, value) ->
                (127 * name.hashCode()) xor when (value) {
                    is Array<*> -> Arrays.hashCode(value)
                    else        -> value.hashCode()
                }
            }
            method == "toString"              -> "@${type.name}(${props.joinToString { (name, value) ->
                "$name=${when (value) {
                    is Array<*> -> Arrays.toString(value)
                    else        -> value.toString()
                }}"
            }})"
            method == "annotationType"        -> type
            method in givenProperties         -> givenProperties[method]
            else                              -> throw NoSuchMethodException(thisMethod.toGenericString())
        }
    }
}

