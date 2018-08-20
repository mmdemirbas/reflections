package org.reflections

import org.apache.logging.log4j.LogManager

fun Annotation.annotationType() = annotationClass.java

private val logger = LogManager.getLogger("org.reflections.Reflections")

fun logDebug(format: String, vararg args: Any?) = logger.debug(format, args)
fun logInfo(format: String, vararg args: Any?) = logger.info(format, args)
fun logWarn(format: String, vararg args: Any?) = logger.warn(format, args)
fun logError(format: String, vararg args: Any?) = logger.error(format, args)


fun <T> not(predicate: (T) -> Boolean): (T) -> Boolean = { it -> !predicate(it) }
fun <T> `in`(collection: Collection<*>): (T) -> Boolean = { it -> collection.contains(it) }

fun String.substringBetween(first: Char, last: Char): String = substring(indexOf(first) + 1, lastIndexOf(last))