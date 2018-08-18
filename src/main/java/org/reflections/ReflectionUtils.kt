package org.reflections

import org.reflections.util.ClasspathHelper
import org.reflections.util.Utils
import org.reflections.util.Utils.isEmpty
import java.lang.reflect.*
import java.util.*
import java.util.regex.Pattern


/**
 * convenient java reflection helper methods
 *
 *
 * 1. some helper methods to get type by name: [.forName] and [.forNames]
 *
 *
 * 2. some helper methods to get all types/methods/fields/constructors/properties matching some predicates, generally:
 * <pre> Set&#60?> result = getAllXXX(type/s, withYYY) </pre>
 *
 * where get methods are:
 *
 *  * [.getAllSuperTypes]
 *  * [.getAllFields]
 *  * [.getAllMethods]
 *  * [.getAllConstructors]
 *
 *
 * and predicates included here all starts with "with", such as
 *
 *  * [.withAnnotation]
 *  * [.withModifier]
 *  * [.withName]
 *  * [.withParameters]
 *  * [.withAnyParameterAnnotation]
 *  * [.withParametersAssignableTo]
 *  * [.withParametersAssignableFrom]
 *  * [.withPrefix]
 *  * [.withReturnType]
 *  * [.withType]
 *  * [.withTypeAssignableTo]
 *
 *
 *
 * <br></br>
 * for example, getting all getters would be:
 * <pre>
 * Set&#60Method> getters = getAllMethods(someClasses,
 * Predicates.and(
 * withModifier(Modifier.PUBLIC),
 * withPrefix("get"),
 * withParametersCount(0)));
</pre> *
 */
object ReflectionUtils {

    /**
     * would include `Object.class` when [.getAllSuperTypes]. default is false.
     */
    val includeObject = false

    //
    private var primitiveNames: List<String>? = null
    private var primitiveTypes: List<Class<*>>? = null
    private var primitiveDescriptors: List<String>? = null

    /**
     * get all super types of given `type`, including, optionally filtered by `predicates`
     *
     *  include `Object.class` if [.includeObject] is true
     */
    fun getAllSuperTypes(type: Class<*>?, vararg predicates: (Class<*>) -> Boolean): Set<Class<*>> {
        val result = mutableSetOf<Class<*>>()
        if (type != null && (includeObject || type != Any::class.java)) {
            result.add(type)
            for (supertype in getSuperTypes(type)) {
                result.addAll(getAllSuperTypes(supertype))
            }
        }
        return filter(result, *predicates)
    }

    /**
     * get the immediate supertype and interfaces of the given `type`
     */
    fun getSuperTypes(type: Class<*>): Set<Class<*>> {
        val result = LinkedHashSet<Class<*>>()
        val superclass = type.superclass
        val interfaces = type.interfaces
        if (superclass != null && (includeObject || superclass != Any::class.java)) {
            result.add(superclass)
        }
        if (interfaces != null && interfaces.size > 0) {
            result.addAll(Arrays.asList(*interfaces))
        }
        return result
    }

    /**
     * get all methods of given `type`, up the super class hierarchy, optionally filtered by `predicates`
     */
    fun getAllMethods(type: Class<*>, vararg predicates: (Method) -> Boolean): Set<Method> {
        val result = mutableSetOf<Method>()
        for (t in getAllSuperTypes(type)) {
            result.addAll(getMethods(t, *predicates))
        }
        return result
    }

    /**
     * get methods of given `type`, optionally filtered by `predicates`
     */
    fun getMethods(t: Class<*>, vararg predicates: (Method) -> Boolean): Set<Method> {
        return if (t.isInterface) filter(t.methods, *predicates) else filter(t.declaredMethods, *predicates)
    }

    /**
     * get all constructors of given `type`, up the super class hierarchy, optionally filtered by `predicates`
     */
    fun getAllConstructors(type: Class<*>, vararg predicates: (Constructor<*>) -> Boolean): Set<Constructor<*>> {
        val result = mutableSetOf<Constructor<*>>()
        for (t in getAllSuperTypes(type)) {
            result.addAll(getConstructors(t, *predicates))
        }
        return result
    }

    /**
     * get constructors of given `type`, optionally filtered by `predicates`
     */
    fun getConstructors(t: Class<*>, vararg predicates: (Constructor<*>) -> Boolean): Set<Constructor<*>> {
        return filter(t.declaredConstructors, *predicates) //explicit needed only for jdk1.5
    }

    /**
     * get all fields of given `type`, up the super class hierarchy, optionally filtered by `predicates`
     */
    fun getAllFields(type: Class<*>, vararg predicates: (Field) -> Boolean): Set<Field> {
        val result = mutableSetOf<Field>()
        for (t in getAllSuperTypes(type)) {
            result.addAll(getFields(t, *predicates))
        }
        return result
    }

    /**
     * get fields of given `type`, optionally filtered by `predicates`
     */
    fun getFields(type: Class<*>, vararg predicates: (Field) -> Boolean): Set<Field> {
        return filter(type.declaredFields, *predicates)
    }

    /**
     * get all annotations of given `type`, up the super class hierarchy, optionally filtered by `predicates`
     */
    fun <T : AnnotatedElement> getAllAnnotations(type: T, vararg predicates: (Annotation) -> Boolean): Set<Annotation> {
        val result = mutableSetOf<Annotation>()
        if (type is Class<*>) {
            for (t in getAllSuperTypes(type as Class<*>)) {
                result.addAll(getAnnotations(t, *predicates))
            }
        } else {
            result.addAll(getAnnotations(type, *predicates))
        }
        return result
    }

    /**
     * get annotations of given `type`, optionally honorInherited, optionally filtered by `predicates`
     */
    fun <T : AnnotatedElement> getAnnotations(type: T, vararg predicates: (Annotation) -> Boolean): Set<Annotation> {
        return filter(type.declaredAnnotations, *predicates)
    }

    /**
     * filter all given `elements` with `predicates`, if given
     */
    fun <T : AnnotatedElement> getAll(elements: Set<T>, vararg predicates: (T) -> Boolean): Set<T> {
        return if (Utils.isEmpty(predicates)) elements else elements.filterAll(*predicates).toSet()
    }

    //predicates

    /**
     * where member name equals given `name`
     */
    fun <T : Member> withName(name: String): (T) -> Boolean {
        return { input -> input != null && input!!.getName() == name }
    }

    /**
     * where member name startsWith given `prefix`
     */
    fun <T : Member> withPrefix(prefix: String): (T) -> Boolean {
        return { input -> input != null && input!!.getName().startsWith(prefix) }
    }

    /**
     * where member's `toString` matches given `regex`
     *
     * for example:
     * <pre>
     * getAllMethods(someClass, withPattern("public void .*"))
    </pre> *
     */
    fun <T : AnnotatedElement> withPattern(regex: String): (T) -> Boolean {
        return { input -> Pattern.matches(regex, input.toString()) }
    }

    /**
     * where element is annotated with given `annotation`
     */
    fun <T : AnnotatedElement> withAnnotation(annotation: Class<out Annotation>): (T) -> Boolean {
        return { input -> input != null && input!!.isAnnotationPresent(annotation) }
    }

    /**
     * where element is annotated with given `annotations`
     */
    fun <T : AnnotatedElement> withAnnotations(vararg annotations: Class<out Annotation>): (T) -> Boolean {
        return { input ->
            input != null && Arrays.equals(annotations, annotationTypes(input!!.getAnnotations()))
        }
    }

    /**
     * where element is annotated with given `annotation`, including member matching
     */
    fun <T : AnnotatedElement> withAnnotation(annotation: Annotation): (T) -> Boolean {
        return { input ->
            (input != null && input!!.isAnnotationPresent(annotation.annotationType()) && areAnnotationMembersMatching(
                    input!!.getAnnotation(annotation.annotationType()),
                    annotation))
        }
    }

    /**
     * where element is annotated with given `annotations`, including member matching
     */
    fun <T : AnnotatedElement> withAnnotations(vararg annotations: Annotation): (T) -> Boolean {
        return { input ->
            input.hasAnnotations(annotations)
        }
    }

    fun <T : AnnotatedElement> T.hasAnnotations(annotations: Array<out Annotation>): Boolean {
        if (this != null) {
            val inputAnnotations = this!!.getAnnotations()
            if (inputAnnotations.size == annotations.size) {
                for (i in inputAnnotations.indices) {
                    if (!areAnnotationMembersMatching(inputAnnotations[i], annotations[i])) {
                        return false
                    }
                }
            }
        }
        return true
    }

    /**
     * when method/constructor parameter types equals given `types`
     */
    fun withParameters(vararg types: Class<*>): (Member) -> Boolean {
        return { input -> Arrays.equals(parameterTypes(input), types) }
    }

    /**
     * when member parameter types assignable to given `types`
     */
    fun withParametersAssignableTo(vararg types: Class<*>): (Member) -> Boolean {
        return { input -> isAssignable(types, parameterTypes(input)) }
    }

    /**
     * when method/constructor parameter types assignable from given `types`
     */
    fun withParametersAssignableFrom(vararg types: Class<*>): (Member) -> Boolean {
        return { input -> isAssignable(parameterTypes(input), types) }
    }

    /**
     * when method/constructor parameters count equal given `count`
     */
    fun withParametersCount(count: Int): (Member) -> Boolean {
        return { input -> input != null && parameterTypes(input)!!.size == count }
    }

    /**
     * when method/constructor has any parameter with an annotation matches given `annotations`
     */
    fun withAnyParameterAnnotation(annotationClass: Class<out Annotation>): (Member) -> Boolean {
        return { input ->
            input != null && annotationTypes(parameterAnnotations(input)).any { input1 -> input1 == annotationClass }
        }
    }

    /**
     * when method/constructor has any parameter with an annotation matches given `annotations`, including member matching
     */
    fun withAnyParameterAnnotation(annotation: Annotation): (Member) -> Boolean {
        return { input ->
            input != null && parameterAnnotations(input).any { input1 ->
                areAnnotationMembersMatching(annotation, input1)
            }
        }
    }

    /**
     * when field type equal given `type`
     */
    fun <T> withType(type: Class<T>): (Field) -> Boolean {
        return { input -> input != null && input!!.getType() == type }
    }

    /**
     * when field type assignable to given `type`
     */
    fun <T> withTypeAssignableTo(type: Class<T>): (Field) -> Boolean {
        return { input -> input != null && type.isAssignableFrom(input!!.getType()) }
    }

    /**
     * when method return type equal given `type`
     */
    fun <T> withReturnType(type: Class<T>): (Method) -> Boolean {
        return { input -> input != null && input!!.getReturnType() == type }
    }

    /**
     * when method return type assignable from given `type`
     */
    fun <T> withReturnTypeAssignableTo(type: Class<T>): (Method) -> Boolean {
        return { input -> input != null && type.isAssignableFrom(input!!.getReturnType()) }
    }

    /**
     * when member modifier matches given `mod`
     *
     * for example:
     * <pre>
     * withModifier(Modifier.PUBLIC)
    </pre> *
     */
    fun <T : Member> withModifier(mod: Int): (T) -> Boolean {
        return { input -> input != null && input!!.getModifiers() and mod != 0 }
    }

    /**
     * when class modifier matches given `mod`
     *
     * for example:
     * <pre>
     * withModifier(Modifier.PUBLIC)
    </pre> *
     */
    fun withClassModifier(mod: Int): (Class<*>) -> Boolean {
        return { input -> input != null && input!!.getModifiers() and mod != 0 }
    }

    //

    /**
     * tries to resolve a java type name to a Class
     *
     * if optional [ClassLoader]s are not specified, then both [org.reflections.util.ClasspathHelper.contextClassLoader] and [org.reflections.util.ClasspathHelper.staticClassLoader] are used
     */
    fun forName(typeName: String, vararg classLoaders: ClassLoader): Class<*>? {
        if (getPrimitiveNames()!!.contains(typeName)) {
            return getPrimitiveTypes()!![getPrimitiveNames()!!.indexOf(typeName)]
        } else {
            var type: String
            if (typeName.contains("[")) {
                val i = typeName.indexOf('[')
                type = typeName.substring(0, i)
                val array = typeName.substring(i).replace("]", "")

                type =
                        if (getPrimitiveNames()!!.contains(type)) getPrimitiveDescriptors()!![getPrimitiveNames()!!.indexOf(
                                type)]
                        else "L$type;"

                type = array + type
            } else {
                type = typeName
            }

            val reflectionsExceptions = mutableListOf<ReflectionsException>()
            for (classLoader in ClasspathHelper.classLoaders(*classLoaders)) {
                if (type.contains("[")) {
                    try {
                        return Class.forName(type, false, classLoader)
                    } catch (e: Throwable) {
                        reflectionsExceptions.add(ReflectionsException("could not get type for name $typeName", e))
                    }

                }
                try {
                    return classLoader.loadClass(type)
                } catch (e: Throwable) {
                    reflectionsExceptions.add(ReflectionsException("could not get type for name $typeName", e))
                }

            }

            if (Reflections.log != null) {
                for (reflectionsException in reflectionsExceptions) {
                    Reflections.log.warn("could not get type for name $typeName from any class loader",
                                         reflectionsException)
                }
            }

            return null
        }
    }

    /**
     * try to resolve all given string representation of types to a list of java types
     */
    fun <T> forNames(classes: Iterable<String>, vararg classLoaders: ClassLoader): List<Class<out T>> {
        val result = ArrayList<Class<out T>>()
        for (className in classes) {
            val type = forName(className, *classLoaders)
            if (type != null) {
                result.add(type as Class<out T>)
            }
        }
        return result
    }

    private fun parameterTypes(member: Member?): Array<Class<*>>? {
        return if (member == null) {
            null
        } else {
            (member as? Executable)?.parameterTypes
        }
    }

    private fun parameterAnnotations(member: Member?): Set<Annotation> {
        val result = mutableSetOf<Annotation>()
        val annotations: Array<Array<Annotation>>?
        annotations = (member as? Executable)?.parameterAnnotations
        for (annotation in annotations!!) {
            Collections.addAll(result, *annotation)
        }
        return result
    }

    private fun annotationTypes(annotations: Iterable<Annotation>): Set<Class<out Annotation>> {
        val result = mutableSetOf<Class<out Annotation>>()
        for (annotation in annotations) {
            result.add(annotation.annotationType())
        }
        return result
    }

    private fun annotationTypes(annotations: Array<Annotation>): Array<Class<out Annotation>> {
        return annotations.indices.map { i ->
            annotations[i].annotationType()
        }.toTypedArray()
    }

    private fun initPrimitives() {
        if (primitiveNames == null) {
            primitiveNames = listOf("boolean", "char", "byte", "short", "int", "long", "float", "double", "void")
            primitiveTypes =
                    listOf<Class<*>>(Boolean::class.javaPrimitiveType!!,
                                     Char::class.javaPrimitiveType!!,
                                     Byte::class.javaPrimitiveType!!,
                                     Short::class.javaPrimitiveType!!,
                                     Int::class.javaPrimitiveType!!,
                                     Long::class.javaPrimitiveType!!,
                                     Float::class.javaPrimitiveType!!,
                                     Double::class.javaPrimitiveType!!,
                                     Void.TYPE)
            primitiveDescriptors = listOf("Z", "C", "B", "S", "I", "J", "F", "D", "V")
        }
    }

    private fun getPrimitiveNames(): List<String>? {
        initPrimitives()
        return primitiveNames
    }

    private fun getPrimitiveTypes(): List<Class<*>>? {
        initPrimitives()
        return primitiveTypes
    }

    private fun getPrimitiveDescriptors(): List<String>? {
        initPrimitives()
        return primitiveDescriptors
    }

    //
    internal fun <T> filter(elements: Array<T>, vararg predicates: (T) -> Boolean): Set<T> {
        return if (isEmpty(predicates)) elements.toSet()
        else elements.asList().filterAll(*predicates).toSet()
    }

    internal fun <T> filter(elements: Iterable<T>, vararg predicates: (T) -> Boolean): Set<T> {
        return if (isEmpty(predicates)) elements.toSet()
        else elements.filterAll(*predicates).toSet()
    }

    private fun areAnnotationMembersMatching(annotation1: Annotation, annotation2: Annotation?): Boolean {
        if (annotation2 != null && annotation1.annotationType() == annotation2.annotationType()) {
            for (method in annotation1.annotationType().getDeclaredMethods()) {
                try {
                    if (method.invoke(annotation1) != method.invoke(annotation2)) {
                        return false
                    }
                } catch (e: Exception) {
                    throw ReflectionsException(String.format("could not invoke method %s on annotation %s",
                                                             method.getName(),
                                                             annotation1.annotationType()), e)
                }

            }
            return true
        }
        return false
    }


    private fun isAssignable(childClasses: Array<out Class<*>>?, parentClasses: Array<out Class<*>>?): Boolean {
        if (childClasses == null) {
            return parentClasses == null || parentClasses.size == 0
        }
        if (childClasses.size != parentClasses!!.size) {
            return false
        }
        for (i in childClasses.indices) {
            if (!parentClasses[i].isAssignableFrom(childClasses[i]) || parentClasses[i] == Any::class.java && childClasses[i] != Any::class.java) {
                return false
            }
        }
        return true
    }

    fun <T> Iterable<T>.filterAll(vararg predicates: (T) -> Boolean) = this.filter { it.all(predicates) }

    fun <T> T.all(predicates: Array<out (T) -> Boolean>) = predicates.all { it(this) }
}
