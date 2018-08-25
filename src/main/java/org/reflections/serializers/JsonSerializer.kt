package org.reflections.serializers

import org.reflections.Configuration
import org.reflections.Reflections
import org.reflections.scanners.Scanner
import org.reflections.util.IndexKey
import org.reflections.util.Multimap
import org.reflections.util.makeParents
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files.write
import kotlin.reflect.KClass

/**
 * serialization of Reflections to json
 *
 *
 * an example of produced json:
 * ```
 * {"store":{"storeMap":
 * {"org.reflections.scanners.TypeAnnotationsScanner":{
 * "org.reflections.TestModel$AC1":["org.reflections.TestModel$C1"],
 * "org.reflections.TestModel$AC2":["org.reflections.TestModel$I3",
 * ...
 * ```
 */
object JsonSerializer : Serializer {
    private val gson: com.google.gson.Gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeAdapter(Reflections::class.java,
                                 com.google.gson.JsonSerializer<Reflections> { reflections, type, context ->
                                     context.serialize(reflections.stores.entries().associate { (scannerType, multimap) ->
                                         scannerType.qualifiedName to multimap.map.entries.associate { (key, values) ->
                                             key.value to values.map { it.value }
                                         }
                                     })
                                 })
            .registerTypeAdapter(Reflections::class.java,
                                 com.google.gson.JsonDeserializer<Reflections> { jsonElement, type, context ->
                                     val reflections = Reflections(Configuration(scanners = emptySet()))
                                     val stores = reflections.stores
                                     (jsonElement as com.google.gson.JsonObject).entrySet()
                                         .forEach { (scannerType, multimap) ->
                                             val map = Multimap<IndexKey, IndexKey>()
                                             (multimap as com.google.gson.JsonObject).entrySet()
                                                 .forEach { (key, value) ->
                                                     (value as com.google.gson.JsonArray).forEach { element ->
                                                         map.put(IndexKey(key), IndexKey(element.asString))
                                                     }
                                                 }
                                             stores.getOrCreate(Class.forName(scannerType).kotlin as KClass<out Scanner>)
                                                 .putAll(map)
                                         }
                                     reflections
                                 }).setPrettyPrinting().create()
    }

    override fun read(inputStream: InputStream) =
            gson.fromJson(InputStreamReader(inputStream), Reflections::class.java)!!

    override fun save(reflections: Reflections, filename: String) = File(filename).makeParents().also { file ->
        write(file.toPath(), toString(reflections).toByteArray(Charset.defaultCharset()))
    }

    override fun toString(reflections: Reflections) = gson.toJson(reflections)!!
}
