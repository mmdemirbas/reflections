package com.mmdemirbas.reflections

import org.apache.logging.log4j.LogManager
import java.lang.reflect.*
import java.net.URL
import java.net.URLDecoder
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.Attributes
import java.util.jar.JarFile
import kotlin.streams.asSequence

// todo: exceptionlar elden geçirilmeli, mesajlar daha anlaşılır detaylar vermeli ya da gereksiz exception'lar atılmamalı

// todo: önemli hataların suppress edilmediğinden emin ol

// todo: json için de bir serialized dosya koy

// todo: JsonSerializer daha human-readable bir default gibi duruyor

// todo: log metotlarında string interpolation kullan

// todo: class loader'ların farklı verilmesi durumu test ediliyor mu? Mesela SubtypesScanner.expandSuperTypes farklı class loader kullanabilir?
// todo: testleri geçir
// todo: file system ile ilgili testleri jimfs kullanarak yap, resources altında öyle dosyalar bulunmasın. çünkü testler olabildiğince self-contained olmalı
// todo: coverage kontrol et, eksik testleri tamamla

// todo: Gereksiz nullable'ları ve null check'leri temizle
// todo: içinde nullable item bulunabilen collection'ları da non-nullable hale getir olabildiğince
// todo: metot input'larında nullable olması pek mantıklı olmayan tipler non-nullable hale getirilsin

// todo: optional dependency'leri değerlendir, mümkünse kaldırmaya çalış veya testlere kaydır. Nihai amaç zero or at most one (javassist) dependency

// todo: guard-clause'ların if'lerinde paranteze gerek yok, tek satırlık return olarak yazılabilir

// todo: URL yerine URI kullanmaya geç

// todo: update urls in javadocs etc
// todo: update javadocs according to the new structure

// todo: library'nin bizim yerimize birşeyleri nasıl tarayacağına karar vermesi yerine
// todo: biz öyle bir mekanizma sunmalıyız ki, sadece sorgulayacağımız şeyleri taramaya izin verebilmeliyiz.
// todo: belki mevcut yapı da bu şekilde tasarlandı ama biraz da java'dan kaynaklı verbosity vardır.

// todo: iki farklı scanner'ı ortak kullanan metotlara daha düzgün ele alınabilir mi?

// todo: javadoc'larda gereksiz fqn'ler var, zaten import edilmişse bunları uzun yazmaya gerek yok

// todo: performans testleri de ekle

// todo: System property'si kullanılan yerler daha iyi encapsulate edilebilir. Testi de kolay olabilir böylece... Jimfs gibi birşeyler lazım yani..

fun Annotation.annotationType() = annotationClass.java

// todo: loaded, indexed gibi ne olup bittiği debug level loglanmalı
val logger = LogManager.getLogger("org.reflections.Reflections")

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

fun AnnotatedElement.fullName(): String = when (this) {
    is Class<*>       -> this.name + when {
        this.isArray -> "[]".repeat(this.generateWhileNotNull { componentType }.size)
        else         -> ""
    }
    is Field          -> "${declaringClass.name}.$name"
    is Method         -> "${declaringClass.name}.$name(${parameterTypes.joinToString { it.fullName() }})"
    is Constructor<*> -> "${this.declaringClass.name}.<init>(${this.parameterTypes.joinToString { it.fullName() }})"
    else              -> TODO()
}

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
    fun putAll(multimap: Multimap<out K, out V>) = multimap.entries().forEach { put(it.first, it.second) }
    fun put(key: K, value: V) = map.getOrPut(key) { mutableSetOf() }.add(value)
    fun get(key: K) = map[key]
    fun keys() = map.keys
    fun values() = map.values.flatten()
    fun entries() = map.entries.flatMap { (k, vs) -> vs.map { k to it } }
    fun keyCount() = map.keys.size
    fun valueCount() = map.values.sumBy(Collection<*>::size)
}

fun <K, V> Map<K, Collection<V>>.toMultimap(): Multimap<K, V> {
    val multimap = Multimap<K, V>()
    forEach { key, values ->
        values.forEach { value ->
            multimap.put(key, value)
        }
    }
    return multimap
}

/**
 * Returns a collection of direct superclass and directly implemented interfaces;
 * - first item will be superclass, if exists.
 * - subsequent items will be interfaces in declaration order.
 */
fun Class<*>.directParentsExceptObject() =
        (listOfNotNull(superclass) - listOf(Any::class.java) + interfaces.filterNotNull())

/**
 * Returns the superclass hierarchy including this class.
 */
fun Class<*>.classHieararchy() = generateWhileNotNull { superclass }

/**
 * Returns the class & interface hierarchy starting with this class, in depth-first order.
 */
fun Class<*>.classAndInterfaceHieararchyExceptObject() = when {
    this == Any::class.java -> emptyList()
    else                    -> dfs { directParentsExceptObject() }
}

/**
 * Returns a depth-first order traversal of the tree rooted at this item and children of each node
 * obtained via [next] method.
 */
fun <T> T.dfs(next: T.() -> Iterable<T>): List<T> = listOf(this) + next().flatMap { it.dfs(next) }

fun <T : AnnotatedElement> withAnnotation(element: T, expected: Annotation) =
        element.isAnnotationPresent(expected.annotationType()) && areAnnotationMembersMatching(expected,
                                                                                               element.getAnnotation(
                                                                                                       expected.annotationType()))

fun withAnyParameterAnnotation(member: Member, expected: Annotation) = parameterAnnotations(member).any { actual ->
    areAnnotationMembersMatching(expected, actual)
}

fun parameterAnnotations(member: Member?) = (member as? Executable)?.parameterAnnotations?.flatten().orEmpty()

fun areAnnotationMembersMatching(expected: Annotation, actual: Annotation?) = when {
    actual == null                                       -> false
    expected.annotationType() == actual.annotationType() -> expected.annotationType().declaredMethods.all { method ->
        tryOrThrow("could not invoke method ${method.name} on annotation ${expected.annotationType()}") {
            method.invoke(expected) == method.invoke(actual)
        }
    }
    else                                                 -> false
}

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
fun classForName(typeName: String, classLoaders: List<ClassLoader> = defaultClassLoaders()): Class<*>? {
    if (primitives.contains(typeName)) return primitives[typeName]!!.type

    val type = typeNameToTypeDescriptor(typeName)
    val exception = RuntimeException("could not get type for name '$typeName' from any class loader")
    classLoaders.forEach { classLoader ->
        if (type.contains('[')) {
            try {
                return Class.forName(type, false, classLoader)
            } catch (e: Throwable) {
                exception.addSuppressed(RuntimeException("could not get type for name '$typeName'", e))
            }
        }
        try {
            return classLoader.loadClass(type)
        } catch (e: Throwable) {
            exception.addSuppressed(RuntimeException("could not get type for name '$typeName'", e))
        }
    }

    if (exception.suppressed.isNotEmpty()) {
        logger.warn("could not get type for name '$typeName' from any class loader", exception)
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
 * Gets the class loader of this library `Configuration.class.getClassLoader()` or null.
 */
fun staticClassLoader() = Scanner::class.java.classLoader ?: null

fun defaultClassLoaders(): List<ClassLoader> = listOfNotNull(contextClassLoader(), staticClassLoader()).distinct()


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
fun URL.manifestUrls(fileSystem: FileSystem = FileSystems.getDefault()) = (listOf(this) + tryOrDefault(emptyList()) {
    // don't do anything on exception, we're going on the assumption it is a jar, which could be wrong
    val cleaned = cleanPath()
    val file = fileSystem.getPath(cleaned)
    val dir = file.parent
    val jar = JarFile(cleaned)
    listOfNotNull(tryToGetValidUrl(file,
                                   dir,
                                   file)) + jar.manifest?.mainAttributes?.getValue(Attributes.Name("Class-Path")).orEmpty().split(
            ' ').dropLastWhile { it.isEmpty() }.mapNotNull {
        tryToGetValidUrl(file, dir, fileSystem.getPath(it))
    }
}).distinctUrls()

fun tryToGetValidUrl(workingDir: Path, path: Path, filename: Path) = listOf(filename,
                                                                            path.resolve(filename),
                                                                            workingDir.resolve(filename)).firstOrNull { it.exists() }?.toUri()?.toURL()

fun URL.cleanPath(): String {
    val path = tryOrDefault(path) { URLDecoder.decode(path, "UTF-8") }.removePrefix("jar:").removePrefix("file:")
    return when {
        path.endsWith("!/") -> path.removeSuffix("!/") + '/'
        else                -> path
    }
}

fun Collection<URL>.distinctUrls() = associate { it.toExternalForm() to it }.values


/**
 * Runs the given block, or returns silently if an exception occurs.
 */
// todo: bu metot Throwables'ın düzgünce yazıldığı kütüphane içine de kopyalansın. Böyle şeyler için tek bir merkez olsa iyi olacak reuse için.
fun tryOrIgnore(block: () -> Unit) = tryCatch(block) { }

/**
 * Runs the given block and returns its result, or `null` if an exception occurs.
 */
fun <R> tryOrNull(block: () -> R) = tryCatch(block) { null }

/**
 * Runs the given block and returns its result, or provided [default] value if an exception occurs.
 */
fun <R> tryOrDefault(default: R, block: () -> R) = tryCatch(block) { default }

/**
 * Runs the given block and returns its result, or throws a [RuntimeException] with the specifid [message] if an exception occurs.
 */
fun <R> tryOrThrow(message: String, block: () -> R) = tryCatch(block) { throw RuntimeException(message, it) }

/**
 * Method equivalent of a try-catch block.
 */
fun <R> tryCatch(`try`: () -> R, catch: (Throwable) -> R) = try {
    `try`()
} catch (e: Throwable) {
    catch(e)
}

fun Iterable<Runnable>.runAllPossiblyParallelAndWait(executorService: ExecutorService?) = when (executorService) {
    null -> forEach { it.run() }
    else -> map { executorService.submit(it::run) }.forEach { it.get()!! }
}

fun Path.exists() = Files.exists(this)
fun Path.walkTopDown() = Files.walk(this).asSequence()
val Path.name get() = fileName.toString()
fun Path.inputStream() = Files.newInputStream(this)
fun Path.canRead() = Files.isReadable(this)
val Path.path get() = this.toString()
val Path.isFile get() = Files.isRegularFile(this)
val Path.isDirectory get() = Files.isDirectory(this)
fun Path.delete() = Files.delete(this)

// todo: temp file create işlemini de Files & Path apisi üzerinden yap, yada kullanımını kaldırıp sil mümkünse...
fun createTempPath() = java.io.File.createTempFile("reflections-", ".tmp").toPath()

fun Path.toURL() = toUri().toURL()
fun Path.bufferedWriter() = Files.newBufferedWriter(this)
fun Path.mkdir() = Files.createDirectory(this)
fun Path.mkdirs() = Files.createDirectories(this)

fun Path.mkParentDirs(): Path {
    parent.mkdirs()
    return this
}

fun Path.nullIfNotExists(): Path? = mapIf(exists()) { this }

fun <R> mapIf(condition: Boolean, block: () -> R) = if (condition) block() else null

fun <X, Y> zipWithPadding(xs: List<X>, ys: List<Y>) = zipWithPadding(xs, ys) { x, y -> Pair(x, y) }

fun <X, Y, Z> zipWithPadding(xs: List<X>, ys: List<Y>, zs: List<Z>) =
        zipWithPadding(xs, ys, zs) { x, y, z -> Triple(x, y, z) }

/**
 * Returns a list of values built from the elements of [xs] and [ys] with the same index
 * using the provided [transform] function applied to each pair of elements.
 * The returned list has length of the longest list. Missing elements assumed null.
 */
fun <X, Y, R> zipWithPadding(xs: List<X>, ys: List<Y>, transform: (x: X?, y: Y?) -> R): List<R> {
    val all = listOf(xs, ys)
    val sizes = all.map { it.size }
    val maxSize = sizes.max() ?: 0
    return (0 until maxSize).map { i -> transform(xs.getOrNull(i), ys.getOrNull(i)) }
}

/**
 * Returns a list of values built from the elements of [xs], [ys] and [zs] with the same index
 * using the provided [transform] function applied to each triple of elements.
 * The returned list has length of the longest list. Missing elements assumed null.
 */
fun <X, Y, Z, R> zipWithPadding(xs: List<X>, ys: List<Y>, zs: List<Z>, transform: (x: X?, y: Y?, z: Z?) -> R): List<R> {
    val all = listOf(xs, ys, zs)
    val sizes = all.map { it.size }
    val maxSize = sizes.max() ?: 0
    return (0 until maxSize).map { i -> transform(xs.getOrNull(i), ys.getOrNull(i), zs.getOrNull(i)) }
}

// todo: throwIfNull biraz gereksiz bir metot sanki, kaldırılsa iyi olabilir
fun <T> T?.throwIfNull(message: String) = this ?: throw RuntimeException(message)