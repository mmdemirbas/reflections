package com.mmdemirbas.reflections

import java.util.regex.Pattern

sealed class Filter {
    abstract fun acceptFqn(fqn: String): Boolean
    abstract fun acceptFile(file: VirtualFile): Boolean

    data class Include(val patternString: String) : Filter() {
        private val pattern = Pattern.compile(patternString)

        override fun acceptFqn(fqn: String) = pattern.matcher(fqn.fqnToResourceName()).matches()

        override fun acceptFile(file: VirtualFile) =
                pattern.matcher(file.relativePath.throwIfNull("relativePath of virtualFile: $file")).matches()

        override fun toString() = "+$patternString"
    }

    data class Exclude(val patternString: String) : Filter() {
        private val pattern = Pattern.compile(patternString)

        override fun acceptFqn(fqn: String) = !pattern.matcher(fqn.fqnToResourceName()).matches()

        override fun acceptFile(file: VirtualFile) =
                !pattern.matcher(file.relativePath.throwIfNull("relativePath of virtualFile: $file")).matches()

        override fun toString() = "-$patternString"
    }

    data class Composite(val filters: List<Filter>) : Filter() {
        override fun acceptFqn(fqn: String) = acceptComposite({ acceptFqn(fqn) })
        override fun acceptFile(file: VirtualFile) = acceptComposite({ acceptFile(file) })

        fun acceptComposite(acceptFn: Filter.() -> Boolean): Boolean {
            var accept = filters.isEmpty() || filters[0] is Exclude
            loop@ for (filter in filters) {
                //skip if this filter won't change
                when {
                    accept -> if (filter is Include) continue@loop
                    else   -> if (filter is Exclude) continue@loop
                }
                accept = filter.acceptFn()
                //break on first exclusion
                if (!accept && filter is Exclude) break
            }
            return accept
        }

        override fun toString() = filters.joinToString()
    }
}

/**
 * Parses a string representation of an include/exclude filter.
 *
 * The given includeExcludeString is a comma separated list of package name segments,
 * each starting with either + or - to indicate include/exclude.
 *
 * For example parsePackagesFilter("-java, -javax, -sun, -com.sun") or parseFilter("+com.myn,-com.myn.excluded").
 * Note that "-java" will block "java.foo" but not "javax.foo".
 *
 * The input strings "-java" and "-java." are equivalent.
 */
fun parsePackagesFilter(includeExcludeString: String) = parseFilter(includeExcludeString) {
    (if (!it.endsWith(".")) "$it." else it).toPrefixRegex()
}

/**
 * Parses a string representation of an include/exclude filter.
 *
 * The given includeExcludeString is a comma separated list of regexes,
 * each starting with either + or - to indicate include/exclude.
 *
 * For example parsePackagesFilter("-java\\..*, -javax\\..*, -sun\\..*, -com\\.sun\\..*")
 * or parseFilter("+com\\.myn\\..*,-com\\.myn\\.excluded\\..*").
 * Note that "-java\\..*" will block "java.foo" but not "javax.foo".
 *
 * See also the more useful [Filter.parsePackagesFilter] method.
 */
fun parseFilter(includeExcludeString: String, transformPattern: (String) -> String = { it }) =
        Filter.Composite(includeExcludeString.split(',').dropLastWhile { it.isEmpty() }.map { string ->
            val trimmed = string.trim { it <= ' ' }
            val prefix = trimmed[0]
            val pattern = transformPattern(trimmed.substring(1))
            when (prefix) {
                '+'  -> Filter.Include(pattern)
                '-'  -> Filter.Exclude(pattern)
                else -> throw RuntimeException("includeExclude should start with either + or -")
            }
        })

fun Class<*>.toPackageNameRegex() = "${`package`.name}.".toPrefixRegex()
fun String.toPrefixRegex() = replace(".", "\\.") + ".*"

fun String.fqnToResourceName() = when {
    isEmpty() -> ""
    else      -> replace('.', '/').substringBefore('$') + ".class"
}