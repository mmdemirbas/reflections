package org.reflections.serializers

import org.reflections.Scanners
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
            .registerTypeAdapter(Scanners::class.java,
                                 com.google.gson.JsonSerializer<Scanners> { scanners, type, context ->
                                     context.serialize(scanners.scanners.associate { scanner ->
                                         scanner.javaClass.name to scanner.store.map.entries.associate { (key, values) ->
                                             key.value to values.map { it.value }
                                         }
                                     })
                                 })
            .registerTypeAdapter(Scanners::class.java,
                                 com.google.gson.JsonDeserializer<Scanners> { jsonElement, type, context ->
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
                                     Scanners(scanners)
                                 }).setPrettyPrinting().create()
    }

    override fun read(inputStream: InputStream) = gson.fromJson(InputStreamReader(inputStream), Scanners::class.java)!!

    override fun save(scanners: Scanners, file: File) {
        write(file.makeParents().toPath(), toString(scanners).toByteArray(Charset.defaultCharset()))
    }

    override fun toString(scanners: Scanners) = gson.toJson(scanners)!!
}
