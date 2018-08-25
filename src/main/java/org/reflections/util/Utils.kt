package org.reflections.util

import org.apache.logging.log4j.LogManager
import org.reflections.Reflections
import org.reflections.ReflectionsException
import org.reflections.scanners.Scanner
import java.io.File
import java.io.IOException
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.Attributes
import java.util.jar.JarFile
import javax.servlet.ServletContext
import kotlin.reflect.KClass


// todo: library'nin bizim yerimize birşeyleri nasıl tarayacağına karar vermesi yerine
// todo: biz öyle bir mekanizma sunmalıyız ki, sadece sorgulayacağımız şeyleri taramaya izin verebilmeliyiz.
// todo: belki mevcut yapı da bu şekilde tasarlandı ama biraz da java'dan kaynaklı verbosity vardır.

// todo: sadece map işlemi için kullanılan index'leri kaldır
// todo: substringBefore ve substringAfter gibi metotlarla değiştirilebilecek kısımları değiştir
// todo: String üzerinden yapılan işlemleri specific type üzerinden yaptır. Örneğin index olarak class ismi yerine class kullanmak gibi...
// todo: Gereksiz nullable'ları ve null check'leri temizle
// todo: gereksiz javadocları sil
// todo: testleri geçir
// todo: file system ile ilgili testleri jimfs kullanarak yap, resources altında öyle dosyalar bulunmasın. çünkü testler olabildiğince self-contained olmalı
// todo: optional dependency'leri değerlendir, mümkünse kaldırmaya çalış veya testlere kaydır. Nihai amaç zero or at most one (javassist) dependency
// todo: assertThat kullanımlarını kaldırmak kodu basitleştirebilir.  zaten bazı yerlerde assertEquals, bazı yerlerde assertThat olmuş
// todo: junit5'e geç ve uzun testleri parçala
// todo: bütün nullable collection'ları non-nullable & empty hale getir
// todo: java functional interface'leri kotlin function type'larıyle değiştir. Mesela Predicate yerine (T) -> Boolean gibi
// todo: gereksiz javadoc'ları sil. mesela include(regex) metodunun üsteündeki includes regex comment'i gibi...
// todo: coverage kontrol et, eksik testleri tamamla
// todo: builder style yazılmış metotlara kotlin sayesinde gerek kalmıyor. Onları kaldır, kullanıldığı yerleri düzelt
// todo: guard-clause'ların if'lerinde paranteze gerek yok, tek satırlık return olarak yazılabilir
// todo: URL yerine URI kullanmaya geç
// todo: bunun gibi entries yerine keys + get erişimleri olan yerleri değiştir
// todo: update urls
// todo: configuration'da scanners'ın default'u da boş olmalı değil mi?
// todo: regex'in string olarak verildiği yerler değiştirilsin, regex mi ne olduğu belli olsun

fun Annotation.annotationType() = annotationClass.java

private val logger = LogManager.getLogger("org.reflections.Reflections")

fun logDebug(format: String, vararg args: Any?) = logger.debug(format, args)
fun logInfo(format: String, vararg args: Any?) = logger.info(format, args)
fun logWarn(format: String, vararg args: Any?) = logger.warn(format, args)
fun logError(format: String, vararg args: Any?) = logger.error(format, args)

fun String.substringBetween(first: Char, last: Char): String = substring(indexOf(first) + 1, lastIndexOf(last))

fun <T> whileNotNull(fn: () -> T?) = fn().generateWhileNotNull { fn() }.asSequence()

fun <T> T?.generateWhileNotNull(next: T.() -> T?): List<T> {
    val result = mutableListOf<T>()
    var value = this
    while (value != null) {
        result += value
        value = value.next()
    }
    return result
}

fun File.makeParents(): File {
    absoluteFile.parentFile.mkdirs()
    return this
}

data class IndexKey(val value: String)

fun AnnotatedElement.fullName(): IndexKey = IndexKey(when (this) {
                                                         is Class<*>       -> this.name + when {
                                                             this.isArray -> "[]".repeat(this.generateWhileNotNull { componentType }.size)
                                                             else         -> ""
                                                         }
                                                         is Field          -> "${declaringClass.name}.$name"
                                                         is Method         -> "${declaringClass.name}.$name(${parameterTypes.joinToString {
                                                             it.fullName().value
                                                         }})"
                                                         is Constructor<*> -> "${this.declaringClass.name}.<init>(${this.parameterTypes.joinToString {
                                                             it.fullName().value
                                                         }})"
                                                         else              -> TODO()
                                                     })

fun KClass<out Scanner>.indexName() = java.simpleName!!

fun String.indexType(): KClass<out Scanner> =
        (Class.forName("${Scanner::class.java.`package`.name}.${this}") as Class<out Scanner>).kotlin


open class DefaultThreadFactory(private val namePrefix: String, private val daemon: Boolean) : ThreadFactory {
    private val group = System.getSecurityManager()?.threadGroup ?: Thread.currentThread().threadGroup
    private val threadNumber = AtomicInteger(1)

    override fun newThread(r: Runnable) = Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0).apply {
        isDaemon = daemon
        priority = Thread.NORM_PRIORITY
    }
}

fun executorService(): ExecutorService? = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                                                                       DefaultThreadFactory("org.reflections-scanner-",
                                                                                            true))


class Multimap<K, V> {
    val map = mutableMapOf<K, MutableSet<V>>()
    val isEmpty get() = map.isEmpty()
    fun size() = map.values.map { it.size }.sum()
    fun putAll(multimap: Multimap<out K, out V>) = multimap.entries().forEach { put(it.key, it.value) }
    fun put(key: K, value: V) = map.getOrPut(key) { mutableSetOf() }.add(value)
    fun get(key: K) = map[key]
    fun keys() = map.keys
    fun values() = map.values.flatten()
    fun entriesGrouped(): Collection<Map.Entry<K, Collection<V>>> = map.entries
    fun entries(): Collection<Map.Entry<K, V>> =
            map.entries.flatMap { (k, vs) -> vs.map { AbstractMap.SimpleImmutableEntry(k, it) } }
}


/**
 * Returns a collection of direct superclass and directly implemented interfaces;
 * - first item will be superclass, if exists.
 * - subsequent items will be interfaces in declaration order.
 */
fun Class<*>.directParentsExceptObject() =
        (listOfNotNull(superclass) - listOf(Any::class.java) + interfaces.filterNotNull()).toSet()

/**
 * Returns the class & interface hierarchy starting with this class, in depth-first order.
 */
fun Class<*>.classAndInterfaceHieararchyExceptObject() = when {
    this == Any::class.java -> emptySet()
    else                    -> dfs { directParentsExceptObject() }.toSet()
}

/**
 * Returns a depth-first order traversal of the tree rooted at this item and children of each node
 * obtained via [next] method.
 */
fun <T> T.dfs(next: T.() -> Iterable<T>): List<T> = listOf(this) + next().flatMap { it.dfs(next) }

fun <T : AnnotatedElement> withAnnotation(it: T, annotation: Annotation) =
        it.isAnnotationPresent(annotation.annotationType()) && areAnnotationMembersMatching(it.getAnnotation(annotation.annotationType()),
                                                                                            annotation)

fun withAnyParameterAnnotation(it: Member, annotationClass: Class<out Annotation>): Boolean =
        parameterAnnotations(it).map { it.annotationType() }.toSet().any { input1 -> input1 == annotationClass }

fun withAnyParameterAnnotation(it: Member, annotation: Annotation) = parameterAnnotations(it).any {
    areAnnotationMembersMatching(annotation, it)
}

fun areAnnotationMembersMatching(annotation1: Annotation, annotation2: Annotation?): Boolean {
    if (annotation2 != null && annotation1.annotationType() == annotation2.annotationType()) {
        annotation1.annotationType().declaredMethods.forEach { method ->
            try {
                if (method.invoke(annotation1) != method.invoke(annotation2)) {
                    return false
                }
            } catch (e: Exception) {
                throw ReflectionsException("could not invoke method ${method.name} on annotation ${annotation1.annotationType()}",
                                           e)
            }
        }
        return true
    }
    return false
}

fun parameterAnnotations(member: Member?) = (member as? Executable)?.parameterAnnotations?.flatten().orEmpty().toSet()

data class Primitive(val type: Class<*>, val descriptor: Char)

val primitives =
        mapOf("boolean" to Primitive(Boolean::class.javaPrimitiveType!!, 'Z'),
              "char" to Primitive(Char::class.javaPrimitiveType!!, 'C'),
              "byte" to Primitive(Byte::class.javaPrimitiveType!!, 'B'),
              "short" to Primitive(Short::class.javaPrimitiveType!!, 'S'),
              "int" to Primitive(Int::class.javaPrimitiveType!!, 'I'),
              "long" to Primitive(Long::class.javaPrimitiveType!!, 'J'),
              "float" to Primitive(Float::class.javaPrimitiveType!!, 'F'),
              "double" to Primitive(Double::class.javaPrimitiveType!!, 'D'),
              "void" to Primitive(Void.TYPE, 'V'))

/**
 * tries to resolve a java type name to a Class
 *
 * if optional [ClassLoader]s are not specified, then both [org.reflections.util.contextClassLoader] and [org.reflections.util.staticClassLoader] are used
 */
fun classForName(typeName: String, classLoaders: List<ClassLoader> = emptyList()): Class<*>? {
    if (primitives.contains(typeName)) return primitives[typeName]!!.type

    val type = typeNameToTypeDescriptor(typeName)
    val exception = ReflectionsException("could not get type for name $typeName from any class loader")
    classLoaders(classLoaders).forEach { classLoader ->
        if (type.contains('[')) {
            try {
                return Class.forName(type, false, classLoader)
            } catch (e: Throwable) {
                exception.addSuppressed(ReflectionsException("could not get type for name $typeName", e))
            }
        }
        try {
            return classLoader.loadClass(type)
        } catch (e: Throwable) {
            exception.addSuppressed(ReflectionsException("could not get type for name $typeName", e))
        }
    }

    if (exception.suppressed.isNotEmpty()) {
        logWarn("could not get type for name $typeName from any class loader", exception)
    }

    return null
}

fun typeNameToTypeDescriptor(typeName: String) = when {
    typeName.contains("[") -> {
        val (componentType, indices) = typeName.split('[', limit = 2)
        val componentTypeDescriptor = primitives[componentType]?.descriptor?.toString() ?: "L$componentType;"
        "[${indices.replace("]", "")}$componentTypeDescriptor"
    }
    else                   -> typeName
}


/**
 * Gets the current thread context class loader `Thread.currentThread().getContextClassLoader()` or null.
 */
fun contextClassLoader() = Thread.currentThread().contextClassLoader ?: null

/**
 * Gets the class loader of this library `Reflections.class.getClassLoader()` or null.
 */
fun staticClassLoader() = Reflections::class.java.classLoader ?: null

/**
 * Returns class Loaders initialized from the specified array.
 *
 * If the input is null or empty, it defaults to both [.contextClassLoader] and [.staticClassLoader]
 */
fun classLoaders(classLoaders: Collection<ClassLoader?>) = when {
    classLoaders.isNotEmpty() -> classLoaders.filterNotNull()
    else                      -> listOfNotNull(contextClassLoader(), staticClassLoader()).distinct()
}

/**
 * Returns a distinct collection of URLs based on a package name.
 *
 *
 * This searches for the package name as a resource, using [ClassLoader.getResources].
 * For example, `urlForPackage(org.reflections)` effectively returns URLs from the
 * classpath containing packages starting with `org.reflections`.
 *
 *
 * If the optional [ClassLoader]s are not specified, then both [.contextClassLoader]
 * and [.staticClassLoader] are used for [ClassLoader.getResources].
 *
 *
 * The returned URLs retainsthe order of the given `classLoaders`.
 *
 * @return the collection of URLs, not null
 */
fun urlForPackage(name: String, classLoaders: List<ClassLoader> = emptyList()) =
        urlForResource(name.replace(".", "/").replace("\\", "/").removePrefix("/"), classLoaders)

/**
 * Returns a distinct collection of URLs based on a resource.
 *
 *
 * This searches for the resource name, using [ClassLoader.getResources].
 * For example, `urlForResource(test.properties)` effectively returns URLs from the
 * classpath containing files of that name.
 *
 *
 * If the optional [ClassLoader]s are not specified, then both [.contextClassLoader]
 * and [.staticClassLoader] are used for [ClassLoader.getResources].
 *
 *
 * The returned URLs retains the order of the given `classLoaders`.
 *
 * @return the collection of URLs, not null
 */
fun urlForResource(resourceName: String?, classLoaders: Collection<ClassLoader> = emptyList()) =
        classLoaders(classLoaders).flatMap<ClassLoader, URL> { classLoader ->
            try {
                classLoader.getResources(resourceName).toList().map { url ->
                    val externalForm = url.toExternalForm()
                    val index = externalForm.lastIndexOf(resourceName!!)
                    when (index) {
                        -1   -> url
                        else -> // Add old url as contextUrl to support exotic url handlers
                            URL(url, externalForm.substring(0, index))
                    }
                }
            } catch (e: IOException) {
                logError("error getting resources for ${resourceName!!}", e)
                emptyList()
            }
        }.distinctUrls()

/**
 * Returns the URL that contains a `Class`.
 *
 *
 * This searches for the class using [ClassLoader.getResource].
 *
 *
 * If the optional [ClassLoader]s are not specified, then both [.contextClassLoader]
 * and [.staticClassLoader] are used for [ClassLoader.getResources].
 *
 * @return the URL containing the class, null if not found
 */
fun urlForClass(aClass: Class<*>, classLoaders: Collection<ClassLoader> = emptyList()): URL? {
    val resourceName = aClass.name.replace(".", "/") + ".class"
    classLoaders(classLoaders).forEach { classLoader ->
        try {
            val url = classLoader.getResource(resourceName)
            if (url != null) {
                val externalForm = url.toExternalForm()
                return URL(externalForm.substring(0,
                                                  externalForm.lastIndexOf(aClass.getPackage().name.replace(".", "/"))))
            }
        } catch (e: MalformedURLException) {
            logWarn("Could not get URL", e)
        }
    }
    return null
}

/**
 * Returns a distinct collection of URLs based on URLs derived from class loaders.
 *
 *
 * This finds the URLs using [URLClassLoader.getURLs] using the specified
 * class loader, searching up the parent hierarchy.
 *
 *
 * If the optional [ClassLoader]s are not specified, then both [.contextClassLoader]
 * and [.staticClassLoader] are used for [ClassLoader.getResources].
 *
 *
 * The returned URLs retains the order of the given `classLoaders`.
 *
 * @return the collection of URLs, not null
 */
fun urlForClassLoader(classLoaders: Collection<ClassLoader?> = emptyList()) =
        classLoaders(classLoaders).flatMap { loader ->
            loader.generateWhileNotNull { parent }
        }.filterIsInstance<URLClassLoader>().flatMap { it.urLs.orEmpty().asList() }.distinctUrls()

/**
 * Returns a distinct collection of URLs based on the `java.class.path` system property.
 *
 *
 * This finds the URLs using the `java.class.path` system property.
 *
 *
 * The returned collection of URLs retains the classpath order.
 *
 * @return the collection of URLs, not null
 */
fun urlForJavaClassPath() =
        System.getProperty("java.class.path")?.split(File.pathSeparator.toRegex()).orEmpty().dropLastWhile { it.isEmpty() }.mapNotNull { path ->
            try {
                File(path).toURI().toURL()
            } catch (e: Exception) {
                logWarn("Could not get URL", e)
                null
            }
        }.distinctUrls()

/**
 * Returns a distinct collection of URLs based on the `WEB-INF/lib` folder.
 *
 *
 * This finds the URLs using the [javax.servlet.ServletContext].
 *
 *
 * The returned URLs retains the order of the given `classLoaders`.
 *
 * @return the collection of URLs, not null
 */
fun ServletContext.webInfLibUrls() =
        getResourcePaths("/WEB-INF/lib").orEmpty().mapNotNull { urlString ->
            tryOrNull { getResource(urlString as String) }
        }.distinctUrls()

/**
 * Returns the URL of the `WEB-INF/classes` folder.
 *
 *
 * This finds the URLs using the [javax.servlet.ServletContext].
 *
 * @return the collection of URLs, not null
 */
fun ServletContext.webInfClassesUrls() = tryOrNull {
    val path = getRealPath("/WEB-INF/classes")
    when {
        path == null        -> getResource("/WEB-INF/classes")
        File(path).exists() -> File(path).toURL()
        else                -> null
    }
}

/**
 * Returns a distinct collection of URLs by expanding the specified URLs with Manifest information.
 *
 *
 * The `MANIFEST.MF` file can contain a `Class-Path` entry that defines additional
 * jar files to be included on the classpath. This method takes each URL in turn, tries to
 * resolve it as a jar file, and if so, adds any additional manifest classpaths.
 * The returned collection of URLs will always contain all the input URLs.
 *
 *
 * The returned URLs retains the input order.
 *
 * @return the collection of URLs, not null
 */
fun Iterable<URL>.manifestUrls() = flatMap { it.manifestUrls() }.distinctUrls()

/**
 * Returns a distinct collection of URLs from a single URL based on the Manifest information.
 *
 *
 * The `MANIFEST.MF` file can contain a `Class-Path` entry that defines additional
 * jar files to be included on the classpath. This method takes a single URL, tries to
 * resolve it as a jar file, and if so, adds any additional manifest classpaths.
 * The returned collection of URLs will always contain the input URL.
 *
 * @return the collection of URLs, not null
 */
fun URL.manifestUrls() = (listOf(this) + tryOrDefault(emptyList()) {
    // don't do anything on exception, we're going on the assumption it is a jar, which could be wrong
    val cleaned = cleanPath()
    val file = File(cleaned)
    val path = file.path
    val dir = file.parent
    val jar = JarFile(cleaned)
    listOfNotNull(tryToGetValidUrl(path,
                                   dir,
                                   cleaned)) + jar.manifest?.mainAttributes?.getValue(Attributes.Name("Class-Path")).orEmpty().split(
            ' ').dropLastWhile { it.isEmpty() }.mapNotNull {
        tryToGetValidUrl(path, dir, it)
    }
}).distinctUrls()

fun tryToGetValidUrl(workingDir: String, path: String, filename: String) =
        listOfNotNull(filename, "$path${File.separator}$filename", "$workingDir${File.separator}$filename", tryOrNull {
            // don't do anything, we're going on the assumption it is a jar, which could be wrong
            URL(filename).file
        }).map { File(it) }.firstOrNull { it.exists() }?.toURI()?.toURL()

fun URL.cleanPath(): String {
    var path = tryOrDefault(path) { URLDecoder.decode(path, "UTF-8") }
    if (path.startsWith("jar:")) path = path.substring("jar:".length)
    if (path.startsWith("file:")) path = path.substring("file:".length)
    if (path.endsWith("!/")) path = path.substring(0, path.lastIndexOf("!/")) + '/'
    return path
}

fun Collection<URL>.distinctUrls() = associate { it.toExternalForm() to it }.values.toSet()


/**
 * Runs the given block and returns its result, or `null` if an exception occurs.
 */
fun <R> tryOrNull(block: () -> R) = tryCatch(block) { null }

/**
 * Runs the given block and returns its result, or provided [default] value if an exception occurs.
 */
fun <R> tryOrDefault(default: R, block: () -> R) = tryCatch(block) { default }

/**
 * Runs the given block and returns its result, or throws a [ReflectionsException] with the given [message].
 */
fun <R> tryOrThrow(message: String, block: () -> R) = tryCatch(block) { throw ReflectionsException(message, it) }

/**
 * Method equivalent of a try-catch block.
 */
fun <R> tryCatch(`try`: () -> R, catch: (Throwable) -> R) = try {
    `try`()
} catch (e: Throwable) {
    catch(e)
}
