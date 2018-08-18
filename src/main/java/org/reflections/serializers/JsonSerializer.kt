package org.reflections.serializers

import com.google.gson.*
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

    private var gson: Gson? = null

    override fun read(inputStream: InputStream): Reflections {
        return getGson().fromJson(InputStreamReader(inputStream), Reflections::class.java)
    }

    override fun save(reflections: Reflections, filename: String): File {
        try {
            val file = Utils.prepareFile(filename)
            write(file.toPath(), toString(reflections).toByteArray(Charset.defaultCharset()))
            return file
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    override fun toString(reflections: Reflections): String {
        return getGson().toJson(reflections)
    }

    private fun getGson(): Gson {
        if (gson == null) {
            gson =
                    GsonBuilder().registerTypeAdapter(Multimap::class.java,
                                                      com.google.gson.JsonSerializer<Multimap<*, *>> { multimap, type, jsonSerializationContext ->
                                                          jsonSerializationContext.serialize(multimap.asMap())
                                                      })
                        .registerTypeAdapter(Multimap::class.java,
                                             JsonDeserializer<Multimap<*, *>> { jsonElement, type, jsonDeserializationContext ->
                                                 val map = Multimap<String, String>()
                                                 for ((key, value) in (jsonElement as JsonObject).entrySet()) {
                                                     for (element in value as JsonArray) {
                                                         map.get(key)!!.add(element.asString)
                                                     }
                                                 }
                                                 map
                                             }).setPrettyPrinting().create()

        }
        return gson!!
    }
}
