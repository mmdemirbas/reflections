package org.reflections

import org.reflections.adapters.ClassAdapterFactory
import org.reflections.scanners.Scanner
import org.reflections.serializers.Serializer
import java.net.URL
import java.util.concurrent.ExecutorService

/**
 * Configuration is used to create a configured instance of [Reflections]
 *
 * it is preferred to use [org.reflections.util.ConfigurationBuilder]
 */
interface Configuration {

    /**
     * the scanner instances used for scanning different metadata
     */
    var scanners: MutableSet<Scanner>

    /**
     * the urls to be scanned
     */
    var urls: MutableSet<URL>

    /**
     * the metadata adapter used to fetch metadata from classes
     */
    val metadataAdapter: ClassAdapterFactory

    /**
     * get the fully qualified name filter used to filter types to be scanned
     */
    var inputsFilter: ((String) -> Boolean)?

    /**
     * executor service used to scan files. if null, scanning is done in a simple for loop
     */
    var executorService: ExecutorService?

    /**
     * the default serializer to use when saving Reflection
     */
    var serializer: Serializer

    /**
     * get class loaders, might be used for resolving methods/fields
     */
    var classLoaders: Array<out ClassLoader>

    /**
     * if true (default), expand super types after scanning, for super types that were not scanned.
     *
     * see [org.reflections.Reflections.expandSuperTypes]
     */
    fun shouldExpandSuperTypes(): Boolean
}
