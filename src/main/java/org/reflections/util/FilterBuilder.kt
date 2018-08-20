package org.reflections.util

import org.reflections.ReflectionsException
import java.util.function.Predicate
import java.util.regex.Pattern

/**
 * Builds include/exclude filters for Reflections.
 *
 *
 * For example:
 * <pre>
 * Predicate&lt;String> filter1 = FilterBuilder.parsePackages("-java, "-javax");
 * Predicate&lt;String> filter2 = new FilterBuilder().include(".*").exclude("java.*");
</pre> *
 */
class FilterBuilder : Predicate<String> {

    private val chain: MutableList<Predicate<String>>?

    constructor() {
        chain = mutableListOf()
    }

    private constructor(filters: Iterable<Predicate<String>>) {
        chain = filters.toMutableList()
    }

    /**
     * include a regular expression
     */
    fun include(regex: String): FilterBuilder {
        return add(Include(regex))
    }

    /**
     * exclude a regular expression
     */
    fun exclude(regex: String): FilterBuilder {
        add(Exclude(regex))
        return this
    }

    /**
     * add a Predicate to the chain of predicates
     */
    fun add(filter: Predicate<String>): FilterBuilder {
        chain!!.add(filter)
        return this
    }

    /**
     * include a package of a given class
     */
    fun includePackage(aClass: Class<*>): FilterBuilder {
        return add(Include(packageNameRegex(aClass)))
    }

    /**
     * exclude a package of a given class
     */
    fun excludePackage(aClass: Class<*>): FilterBuilder {
        return add(Exclude(packageNameRegex(aClass)))
    }

    /**
     * include packages of given prefixes
     */
    fun includePackage(vararg prefixes: String): FilterBuilder {
        for (prefix in prefixes) {
            add(Include(prefix(prefix)))
        }
        return this
    }

    /**
     * exclude a package of a given prefix
     */
    fun excludePackage(prefix: String): FilterBuilder {
        return add(Exclude(prefix(prefix)))
    }

    override fun toString(): String {
        return chain!!.joinToString()
    }

    override fun test(regex: String): Boolean {
        var accept = chain == null || chain.isEmpty() || chain[0] is Exclude

        if (chain != null) {
            for (filter in chain) {
                if (accept && filter is Include) {
                    continue
                } //skip if this filter won't change
                if (!accept && filter is Exclude) {
                    continue
                }
                accept = filter.test(regex)
                if (!accept && filter is Exclude) {
                    break
                } //break on first exclusion
            }
        }
        return accept
    }

    fun asPredicate(): (String) -> Boolean {
        return { test(it) }
    }

    abstract class Matcher(regex: String) : Predicate<String> {

        internal val pattern: Pattern = Pattern.compile(regex)

        abstract override fun test(regex: String): Boolean

        override fun toString(): String {
            return pattern.pattern()
        }
    }

    class Include(patternString: String) : Matcher(patternString) {

        override fun test(regex: String): Boolean {
            return pattern.matcher(regex).matches()
        }

        override fun toString(): String {
            return '+' + super.toString()
        }
    }

    class Exclude(patternString: String) : Matcher(patternString) {

        override fun test(regex: String): Boolean {
            return !pattern.matcher(regex).matches()
        }

        override fun toString(): String {
            return '-' + super.toString()
        }
    }

    companion object {

        private fun packageNameRegex(aClass: Class<*>): String {
            return prefix(aClass.getPackage().name + '.')
        }

        fun prefix(qualifiedName: String): String {
            return qualifiedName.replace(".", "\\.") + ".*"
        }

        /**
         * Parses a string representation of an include/exclude filter.
         *
         *
         * The given includeExcludeString is a comma separated list of regexes,
         * each starting with either + or - to indicate include/exclude.
         *
         *
         * For example parsePackages("-java\\..*, -javax\\..*, -sun\\..*, -com\\.sun\\..*")
         * or parse("+com\\.myn\\..*,-com\\.myn\\.excluded\\..*").
         * Note that "-java\\..*" will block "java.foo" but not "javax.foo".
         *
         *
         * See also the more useful [FilterBuilder.parsePackages] method.
         */
        fun parse(includeExcludeString: String): FilterBuilder {

            if (includeExcludeString.isEmpty()) {
                return FilterBuilder()
            }

            val filters = mutableListOf<Predicate<String>>()
            for (string in includeExcludeString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                val trimmed = string.trim { it <= ' ' }
                val prefix = trimmed[0]
                val pattern = trimmed.substring(1)

                val filter: Predicate<String>
                filter = when (prefix) {
                    '+'  -> Include(pattern)
                    '-'  -> Exclude(pattern)
                    else -> throw ReflectionsException("includeExclude should start with either + or -")
                }

                filters.add(filter)
            }

            return FilterBuilder(filters)
        }

        /**
         * Parses a string representation of an include/exclude filter.
         *
         *
         * The given includeExcludeString is a comma separated list of package name segments,
         * each starting with either + or - to indicate include/exclude.
         *
         *
         * For example parsePackages("-java, -javax, -sun, -com.sun") or parse("+com.myn,-com.myn.excluded").
         * Note that "-java" will block "java.foo" but not "javax.foo".
         *
         *
         * The input strings "-java" and "-java." are equivalent.
         */
        fun parsePackages(includeExcludeString: String): FilterBuilder {

            if (includeExcludeString.isEmpty()) {
                return FilterBuilder()
            }

            val filters = mutableListOf<Predicate<String>>()
            for (string in includeExcludeString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                val trimmed = string.trim { it <= ' ' }
                val prefix = trimmed[0]
                var pattern = trimmed.substring(1)
                if (!pattern.endsWith(".")) {
                    pattern += "."
                }
                pattern = prefix(pattern)

                val filter: Predicate<String>
                filter = when (prefix) {
                    '+'  -> Include(pattern)
                    '-'  -> Exclude(pattern)
                    else -> throw ReflectionsException("includeExclude should start with either + or -")
                }

                filters.add(filter)
            }

            return FilterBuilder(filters)
        }
    }
}
