package org.reflections.serializers

import org.reflections.scanners.CompositeScanner
import org.reflections.scanners.SimpleScanner
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

    override fun read(inputStream: InputStream) =
            gson.fromJson(InputStreamReader(inputStream), CompositeScanner::class.java)!!.scanners

    override fun save(scanners: List<SimpleScanner<*>>, file: File) {
        write(file.makeParents().toPath(), toString(scanners).toByteArray(Charset.defaultCharset()))
    }

    override fun toString(scanners: List<SimpleScanner<*>>) = gson.toJson(CompositeScanner(scanners))!!
}
