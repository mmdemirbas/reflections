package org.reflections.serializers;

import com.google.common.collect.*;
import com.google.common.io.Files;
import com.google.gson.*;
import org.reflections.Reflections;
import org.reflections.util.Utils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * serialization of Reflections to json
 *
 * <p>an example of produced json:
 * <pre>
 * {"store":{"storeMap":
 *    {"org.reflections.scanners.TypeAnnotationsScanner":{
 *       "org.reflections.TestModel$AC1":["org.reflections.TestModel$C1"],
 *       "org.reflections.TestModel$AC2":["org.reflections.TestModel$I3",
 * ...
 * </pre>
 */
public class JsonSerializer implements Serializer {

    private Gson gson;

    @Override
    public Reflections read(InputStream inputStream) {
        return getGson().fromJson(new InputStreamReader(inputStream), Reflections.class);
    }

    @Override
    public File save(Reflections reflections, String filename) {
        try {
            File file = Utils.prepareFile(filename);
            Files.write(toString(reflections), file, Charset.defaultCharset());
            return file;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString(Reflections reflections) {
        return getGson().toJson(reflections);
    }

    private Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder().registerTypeAdapter(Multimap.class,
                                                         (com.google.gson.JsonSerializer<Multimap>) (multimap, type, jsonSerializationContext) -> jsonSerializationContext
                                                                 .serialize(multimap.asMap()))
                                    .registerTypeAdapter(Multimap.class,
                                                         (JsonDeserializer<Multimap>) (jsonElement, type, jsonDeserializationContext) -> {
                                                             SetMultimap<String, String> map = Multimaps.newSetMultimap(
                                                                     new HashMap<>(),
                                                                     Sets::newHashSet);
                                                             for (Entry<String, JsonElement> entry : ((JsonObject) jsonElement)
                                                                     .entrySet()) {
                                                                 for (JsonElement element : (JsonArray) entry.getValue()) {
                                                                     map.get(entry.getKey()).add(element.getAsString());
                                                                 }
                                                             }
                                                             return map;
                                                         })
                                    .setPrettyPrinting()
                                    .create();

        }
        return gson;
    }
}
