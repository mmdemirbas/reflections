package org.reflections.serializers

import org.reflections.scanners.CompositeScanner
import org.reflections.scanners.SimpleScanner
import java.io.Reader

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
    // todo: buradaki JSON dönüşümü library kullanmadan yapılabilse güzel olur
    private val gson: com.google.gson.Gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeAdapter(CompositeScanner::class.java,
                                 com.google.gson.JsonSerializer<CompositeScanner> { scanners, type, context ->
                                     context.serialize(scanners.scanners.associate { scanner ->
                                         scanner.javaClass.name to scanner.stringEntries()
                                     })
                                 })
            .registerTypeAdapter(CompositeScanner::class.java,
                                 com.google.gson.JsonDeserializer<CompositeScanner> { jsonElement, type, context ->
                                     val scanners = mutableListOf<SimpleScanner<*>>()
                                     (jsonElement as com.google.gson.JsonObject).entrySet()
                                         .forEach { (scannerType, multimap) ->
                                             val scanner = Class.forName(scannerType).newInstance() as SimpleScanner<*>
                                             (multimap as com.google.gson.JsonObject).entrySet()
                                                 .forEach { (key, value) ->
                                                     (value as com.google.gson.JsonArray).forEach { element ->
                                                         scanner.addEntry(key, element.asString)
                                                     }
                                                 }
                                             scanners.add(scanner)
                                         }
                                     CompositeScanner(scanners)
                                 }).setPrettyPrinting().create()
    }

    override fun read(reader: Reader) = gson.fromJson(reader, CompositeScanner::class.java)!!
    override fun write(scanners: CompositeScanner, writer: Appendable) = gson.toJson(scanners, writer)
}
