package org.reflections.serializers

import org.reflections.Configuration
import org.reflections.scanners.Scanner
import org.reflections.util.Datum
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
            .registerTypeAdapter(Configuration::class.java,
                                 com.google.gson.JsonSerializer<Configuration> { configuration, type, context ->
                                     context.serialize(configuration.scanners.associate { scanner ->
                                         scanner.javaClass.name to scanner.store.map.entries.associate { (key, values) ->
                                             key.value to values.map { it.value }
                                         }
                                     })
                                 })
            .registerTypeAdapter(Configuration::class.java,
                                 com.google.gson.JsonDeserializer<Configuration> { jsonElement, type, context ->
                                     val scanners = mutableListOf<Scanner>()
                                     (jsonElement as com.google.gson.JsonObject).entrySet()
                                         .forEach { (scannerType, multimap) ->
                                             val scanner = Class.forName(scannerType).newInstance() as Scanner
                                             (multimap as com.google.gson.JsonObject).entrySet()
                                                 .forEach { (key, value) ->
                                                     (value as com.google.gson.JsonArray).forEach { element ->
                                                         scanner.store.put(Datum(key), Datum(element.asString))
                                                     }
                                                 }
                                             scanners.add(scanner)
                                         }
                                     Configuration(scanners = scanners.toSet())
                                 }).setPrettyPrinting().create()
    }

    override fun read(inputStream: InputStream) =
            gson.fromJson(InputStreamReader(inputStream), Configuration::class.java)!!

    override fun save(configuration: Configuration, file: File) {
        write(file.makeParents().toPath(), toString(configuration).toByteArray(Charset.defaultCharset()))
    }

    override fun toString(configuration: Configuration) = gson.toJson(configuration)!!
}
