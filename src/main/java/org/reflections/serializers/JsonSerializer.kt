package org.reflections.serializers

import org.reflections.Multimap
import org.reflections.Reflections
import org.reflections.util.Utils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files.write

/**
 * serialization of Reflections to json
 *
 *
 * an example of produced json:
 * <pre>
 * {"store":{"storeMap":
 * {"org.reflections.scanners.TypeAnnotationsScanner":{
 * "org.reflections.TestModel$AC1":["org.reflections.TestModel$C1"],
 * "org.reflections.TestModel$AC2":["org.reflections.TestModel$I3",
 * ...
</pre> *
 */
class JsonSerializer : Serializer {

    private var gson: com.google.gson.Gson? = null

    override fun read(inputStream: InputStream) =
            getGson().fromJson(InputStreamReader(inputStream), Reflections::class.java)!!

    override fun save(reflections: Reflections, filename: String): File {
        try {
            val file = Utils.prepareFile(filename)
            write(file.toPath(), toString(reflections).toByteArray(Charset.defaultCharset()))
            return file
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun toString(reflections: Reflections) = getGson().toJson(reflections)!!

    private fun getGson(): com.google.gson.Gson {
        if (gson == null) {
            gson =
                    com.google.gson.GsonBuilder()
                        .registerTypeAdapter(Multimap::class.java,
                                             com.google.gson.JsonSerializer<Multimap<*, *>> { multimap, type, jsonSerializationContext ->
                                                 jsonSerializationContext.serialize(multimap.asMap())
                                             })
                        .registerTypeAdapter(Multimap::class.java,
                                             com.google.gson.JsonDeserializer<Multimap<*, *>> { jsonElement, type, jsonDeserializationContext ->
                                                 val map = Multimap<String, String>()
                                                 for ((key, value) in (jsonElement as com.google.gson.JsonObject).entrySet()) {
                                                     for (element in value as com.google.gson.JsonArray) {
                                                         map.get(key)!!.add(element.asString)
                                                     }
                                                 }
                                                 map
                                             }).setPrettyPrinting().create()

        }
        return gson!!
    }
}
