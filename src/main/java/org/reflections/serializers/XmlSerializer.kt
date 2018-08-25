package org.reflections.serializers

import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.reflections.Reflections
import org.reflections.util.IndexKey
import org.reflections.util.indexName
import org.reflections.util.makeParents
import org.reflections.util.tryOrThrow
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.io.StringWriter
import java.io.Writer

/**
 * serialization of Reflections to xml
 *
 *
 * an example of produced xml:
 * ```
 * <?xml version="1.0" encoding="UTF-8"?>
 *
 * <Reflections>
 * <SubTypesScanner>
 * <entry>
 * <key>com.google.inject.Module</key>
 * <values>
 * <value>fully.qualified.name.1</value>
 * <value>fully.qualified.name.2</value>
 * ...
 * ```
 */
object XmlSerializer : Serializer {
    override fun read(inputStream: InputStream): Reflections {
        val reflections = Reflections()
        tryOrThrow("could not read.") {
            SAXReader().read(inputStream).rootElement.elements().forEach { e1 ->
                val index = e1 as org.dom4j.Element
                index.elements().forEach { e2 ->
                    val entry = e2 as org.dom4j.Element
                    val key = entry.element("key")
                    entry.element("values").elements().map { it as org.dom4j.Node }.forEach {
                        reflections.stores.getOrCreate(index.name).put(IndexKey(key.text), IndexKey(it.text))
                    }
                }
            }
        }
        return reflections
    }

    override fun save(reflections: Reflections, filename: String) = tryOrThrow("could not save to file $filename") {
        val file = File(filename).makeParents()
        FileWriter(file).use { writer ->
            writeTo(writer, reflections)
        }
        file
    }

    override fun toString(reflections: Reflections) = StringWriter().use { writer ->
        writeTo(writer, reflections)
        writer.toString()
    }

    private fun writeTo(fileWriter: Writer, reflections: Reflections) {
        val xmlWriter = XMLWriter(fileWriter, OutputFormat.createPrettyPrint())
        xmlWriter.write(createDocument(reflections))
        xmlWriter.close()
    }

    private fun createDocument(reflections: Reflections): org.dom4j.Document {
        val document = org.dom4j.DocumentFactory.getInstance().createDocument()
        val root = document.addElement("Reflections")
        reflections.stores.entries().forEach { (indexKey, index) ->
            val indexElement = root.addElement(indexKey.indexName())
            index.entriesGrouped().forEach { (key, values) ->
                val entryElement = indexElement.addElement("entry")
                entryElement.addElement("key").text = key.value
                val valuesElement = entryElement.addElement("values")
                values.forEach { value ->
                    valuesElement.addElement("value").text = value.value
                }
            }
        }
        return document
    }
}
