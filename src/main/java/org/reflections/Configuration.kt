package org.reflections

import org.reflections.scanners.Scanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.defaultClassLoaders
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.regex.Pattern

/**
 * Configuration is used to create a configured instance of [Reflections].
 *
 * @property scanners  the scanner instances used for scanning different metadata
 * @property urls the urls to be scanned
 * @property filter the fully qualified name filter used to filter types to be scanned
 * @property executorService executor service used to scan files. if null, scanning is done in a simple for loop
 * @property classLoaders the class loaders, might be used for resolving methods/fields
 * @constructor
 */
data class Configuration(var scanners: Set<Scanner> = setOf(TypeAnnotationsScanner(), SubTypesScanner()),
                         var urls: Set<URL> = emptySet(),
                         var filter: Filter = Filter.Composite(emptyList()),
                         var executorService: ExecutorService? = null,
                         var classLoaders: List<ClassLoader> = defaultClassLoaders())


sealed class Filter {
    abstract fun test(s: String): Boolean

    data class Include(val patternString: String) : Filter() {
        private val pattern = Pattern.compile(patternString)
        override fun test(s: String) = pattern.matcher(s).matches()
        override fun toString() = "+$patternString"
    }

    data class Exclude(val patternString: String) : Filter() {
        private val pattern = Pattern.compile(patternString)
        override fun test(s: String) = !pattern.matcher(s).matches()
        override fun toString() = "-$patternString"
    }

    data class Composite(val filters: List<Filter>) : Filter() {
        override fun test(s: String): Boolean {
            var accept = filters.isEmpty() || filters[0] is Exclude
            loop@ for (filter in filters) {
                //skip if this filter won't change
                when {
                    accept -> if (filter is Include) continue@loop
                    else   -> if (filter is Exclude) continue@loop
                }
                accept = filter.test(s)
                //break on first exclusion
                if (!accept && filter is Exclude) break
            }
            return accept
        }

        override fun toString() = filters.joinToString()
    }

    companion object {

        /**
         * Parses a string representation of an include/exclude filter.
         *
         * The given includeExcludeString is a comma separated list of package name segments,
         * each starting with either + or - to indicate include/exclude.
         *
         * For example parsePackages("-java, -javax, -sun, -com.sun") or parse("+com.myn,-com.myn.excluded").
         * Note that "-java" will block "java.foo" but not "javax.foo".
         *
         * The input strings "-java" and "-java." are equivalent.
         */
        fun parsePackages(includeExcludeString: String) = parse(includeExcludeString) {
            (if (!it.endsWith(".")) "$it." else it).toPrefixRegex()
        }

        /**
         * Parses a string representation of an include/exclude filter.
         *
         * The given includeExcludeString is a comma separated list of regexes,
         * each starting with either + or - to indicate include/exclude.
         *
         * For example parsePackages("-java\\..*, -javax\\..*, -sun\\..*, -com\\.sun\\..*")
         * or parse("+com\\.myn\\..*,-com\\.myn\\.excluded\\..*").
         * Note that "-java\\..*" will block "java.foo" but not "javax.foo".
         *
         * See also the more useful [Filter.parsePackages] method.
         */
        fun parse(includeExcludeString: String, transformPattern: (String) -> String = { it }) =
                Composite(includeExcludeString.split(',').dropLastWhile { it.isEmpty() }.map { string ->
                    val trimmed = string.trim { it <= ' ' }
                    val prefix = trimmed[0]
                    val pattern = transformPattern(trimmed.substring(1))
                    when (prefix) {
                        '+'  -> Include(pattern)
                        '-'  -> Exclude(pattern)
                        else -> throw RuntimeException("includeExclude should start with either + or -")
                    }
                })
    }
}

fun String.toPrefixRegex() = replace(".", "\\.") + ".*"
fun Class<*>.toPackageNameRegex() = "${`package`.name}.".toPrefixRegex()