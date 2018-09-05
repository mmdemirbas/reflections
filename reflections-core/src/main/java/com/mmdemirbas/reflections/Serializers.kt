package com.mmdemirbas.reflections

import org.dom4j.DocumentFactory
import org.dom4j.Element
import org.dom4j.Node
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import java.io.Reader
import java.io.StringWriter
import java.lang.Appendable
import java.nio.file.Path

// TODO: Reflections outputunu orjinaliyle %100 uyumlu hale getir. En azından bunu da support et, hatta default yap.
// TODO: Orjinalden buna kolay migration yapabilmek için guide oluşturulabilir. Minimum iş yaptırabilmek için gerekli gerekirse dummy sınıflar içeren migration modülü oluşturulabilir.

/**
 * Serializer of a [CompositeScanner] instances
 */
interface Serializer {
    fun read(reader: Reader): CompositeScanner
    fun write(scanners: CompositeScanner, writer: Appendable)

    fun write(scanners: CompositeScanner, path: Path) = tryOrThrow("could not write to path $path") {
        path.mkParentDirs().bufferedWriter().use { writer -> write(scanners, writer) }
    }

    fun toString(scanners: CompositeScanner) = StringBuilder().apply { write(scanners, this) }.toString()
}


enum class XmlSerializerVersion { v0_original, v1_compact }

/**
 * serialization of Reflections to xml
 *
 *
 * an example of produced xml:
 * ```
 * <?xml version="1.0" encoding="UTF-8"?>
 *
 * <Reflections>
 *   <SubTypesScanner>
 *     <entry>
 *       <key>com.google.inject.Module</key>
 *       <values>
 *         <value>fully.qualified.name.1</value>
 *         <value>fully.qualified.name.2</value>
 * ...
 * ```
 */
data class XmlSerializer(val version: XmlSerializerVersion = XmlSerializerVersion.v0_original) : Serializer {
    // todo: basitlik açısından JAXB object'e dönüştürülebilir
    override fun read(reader: Reader): CompositeScanner {
        return tryOrThrow("could not read.") {
            val scanners = SAXReader().read(reader).rootElement.elements().map {
                val scannerNode = it as Element
                val scannerClassName = scannerNode.name
                val scannerClass = Class.forName(scannerClassName)
                val scanner = scannerClass!!.newInstance() as SimpleScanner<*>
                scannerNode.elements().forEach {
                    val entryElement = it as Element
                    val keyElement = entryElement.element("key")
                    entryElement.element("values").elements().map { it as Node }.forEach { valueElement ->
                        scanner.addEntry(keyElement.text, valueElement.text)
                    }
                }
                scanner
            }
            CompositeScanner(scanners)
        }
    }

    override fun write(scanners: CompositeScanner, writer: Appendable) {
        val document = DocumentFactory.getInstance().createDocument()
        val root = document.addElement("reflections")
        scanners.scanners.forEach { scanner ->
            val scannerClass = scanner.javaClass
            val scannerNodeName = scannerClass.name!!
            val scannerNode = root.addElement(scannerNodeName)
            scanner.entries().forEach { (key, values) ->
                val entryElement = scannerNode.addElement("entry")
                entryElement.addElement("key").text = key
                val valuesElement = entryElement.addElement("values")
                values.forEach { value ->
                    valuesElement.addElement("value").text = value
                }
            }
        }

        val stringWriter = StringWriter()
        val xmlWriter = XMLWriter(stringWriter, OutputFormat.createPrettyPrint())
        xmlWriter.write(document)
        xmlWriter.close()
        writer.append(stringWriter.toString())
    }
}

enum class JsonSerializerVersion { v0_original, v1_compact }

/**
 * serialization of Reflections to json
 *
 *
 * an example of produced json:
 * ```
 * {"store":
 *   {"storeMap":
 *     {"org.reflections.TypeAnnotationsScanner":
 *       {
 *         "org.reflections.TestModel$AC1":["org.reflections.TestModel$C1"],
 *         "org.reflections.TestModel$AC2":["org.reflections.TestModel$I3",
 * ...
 * ```
 */
data class JsonSerializer(val version: JsonSerializerVersion = JsonSerializerVersion.v0_original) : Serializer {
    // todo: buradaki JSON dönüşümü library kullanmadan yapılabilse güzel olur
    private val gson: com.google.gson.Gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeAdapter(CompositeScanner::class.java,
                                 com.google.gson.JsonDeserializer<CompositeScanner> { jsonElement, type, context ->
                                     val scanners =
                                             (jsonElement as com.google.gson.JsonObject).entrySet()
                                                 .map { (scannerType, multimap) ->
                                                     val scanner =
                                                             Class.forName(scannerType).newInstance() as SimpleScanner<*>
                                                     (multimap as com.google.gson.JsonObject).entrySet()
                                                         .forEach { (key, value) ->
                                                             (value as com.google.gson.JsonArray).forEach { element ->
                                                                 scanner.addEntry(key, element.asString)
                                                             }
                                                         }
                                                     scanner
                                                 }
                                     CompositeScanner(scanners)
                                 }).setPrettyPrinting().create()
    }

    fun CompositeScanner.toMap(): List<Map<String, MutableMap<String, MutableSet<String>>>> {
        return scanners.map { scanner ->
            mapOf(scanner.javaClass.name to scanner.store.map)
        }
    }

    override fun read(reader: Reader) = CompositeScanner((gson.fromJson(reader,
                                                                        List::class.java) as List<Map<String, Map<String, List<String>>>>).map {
        val single = it.entries.single()
        val scannerClassName = single.key
        val scanner = Class.forName(scannerClassName).newInstance() as SimpleScanner<*>
        scanner.store.putAll(single.value.toMultimap())
        scanner
    })

    override fun write(scanners: CompositeScanner, writer: Appendable) = gson.toJson(scanners.toMap(), writer)
}

// todo: bitince orginal kütüphaneyle performans açısından da karşılaştır