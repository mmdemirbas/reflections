package org.reflections

import org.reflections.scanners.Scanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import java.net.URL
import java.util.concurrent.ExecutorService

/**
 * Configuration is used to create a configured instance of [Reflections].
 *
 * @property scanners  the scanner instances used for scanning different metadata
 * @property urls the urls to be scanned
 * @property filter the fully qualified name filter used to filter types to be scanned
 * @property executorService executor service used to scan files. if null, scanning is done in a simple for loop
 * @property classLoaders the class loaders, might be used for resolving methods/fields
 * @property expandSuperTypes if true (default), expand super types after scanning, for super types that were not scanned. see [org.reflections.Reflections.expandSuperTypes]
 * @constructor
 */
data class Configuration(var scanners: Set<Scanner> = setOf(TypeAnnotationsScanner(), SubTypesScanner()),
                         var urls: Set<URL> = emptySet(),
                         var filter: Filter? = null,
                         var executorService: ExecutorService? = null,
                         var classLoaders: List<ClassLoader> = emptyList(),
                         var expandSuperTypes: Boolean = true)