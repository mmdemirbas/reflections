package org.reflections.serializers

import org.reflections.Configuration
import org.reflections.Reflections
import org.reflections.scanners.Scanner
import org.reflections.util.IndexKey
import org.reflections.util.makeParents
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files.write

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
                                     context.serialize(reflections.stores.associate { scanner ->
                                         scanner.javaClass.name to scanner.store.map.entries.associate { (key, values) ->
                                             key.value to values.map { it.value }
                                         }
                                     })
                                 })
            .registerTypeAdapter(Reflections::class.java,
                                 com.google.gson.JsonDeserializer<Reflections> { jsonElement, type, context ->
                                     val scanners = mutableListOf<Scanner>()
                                     (jsonElement as com.google.gson.JsonObject).entrySet()
                                         .forEach { (scannerType, multimap) ->
                                             val scanner = Class.forName(scannerType).newInstance() as Scanner
                                             (multimap as com.google.gson.JsonObject).entrySet()
                                                 .forEach { (key, value) ->
                                                     (value as com.google.gson.JsonArray).forEach { element ->
                                                         scanner.store.put(IndexKey(key), IndexKey(element.asString))
                                                     }
                                                 }
                                             scanners.add(scanner)
                                         }
                                     Reflections(Configuration(scanners = scanners.toSet()))
                                 }).setPrettyPrinting().create()
    }

    override fun read(inputStream: InputStream) =
            gson.fromJson(InputStreamReader(inputStream), Reflections::class.java)!!

    override fun save(reflections: Reflections, filename: String) = File(filename).makeParents().also { file ->
        write(file.toPath(), toString(reflections).toByteArray(Charset.defaultCharset()))
    }

    override fun toString(reflections: Reflections) = gson.toJson(reflections)!!
}
