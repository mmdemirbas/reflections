package org.reflections

import java.util.regex.Pattern

sealed class Filter {
    abstract fun test(regex: String): Boolean

    data class Include(val patternString: String) : Filter() {
        private val pattern: Pattern = Pattern.compile(patternString)

        override fun test(regex: String) = pattern.matcher(regex).matches()
        override fun toString() = "+$patternString"
    }

    data class Exclude(val patternString: String) : Filter() {
        private val pattern: Pattern = Pattern.compile(patternString)

        override fun test(regex: String) = !pattern.matcher(regex).matches()
        override fun toString() = "-$patternString"
    }

    data class Composite(val filters: List<Filter>) : Filter() {
        override fun test(regex: String): Boolean {
            var accept = filters.isEmpty() || filters[0] is Exclude
            loop@ for (filter in filters) {
                //skip if this filter won't change
                when {
                    accept -> if (filter is Include) continue@loop
                    else   -> if (filter is Exclude) continue@loop
                }
                accept = filter.test(regex)
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
                        else -> throw ReflectionsException("includeExclude should start with either + or -")
                    }
                })
    }
}

fun String.toPrefixRegex() = replace(".", "\\.") + ".*"
fun Class<*>.toPackageNameRegex() = "${`package`.name}.".toPrefixRegex()