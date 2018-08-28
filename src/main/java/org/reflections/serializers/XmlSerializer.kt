package org.reflections.serializers

import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.reflections.Configuration
import org.reflections.Reflections
import org.reflections.scanners.Scanner
import org.reflections.util.Datum
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
        return tryOrThrow("could not read.") {
            val scanners = mutableListOf<Scanner>()
            SAXReader().read(inputStream).rootElement.elements().forEach { e1 ->
                val index = e1 as org.dom4j.Element
                val scanner = index.name.indexNameToClass().newInstance() as Scanner
                index.elements().forEach { e2 ->
                    val entry = e2 as org.dom4j.Element
                    val key = entry.element("key")
                    entry.element("values").elements().map { it as org.dom4j.Node }.forEach {
                        scanner.store.put(Datum(key.text), Datum(it.text))
                    }
                }
                scanners.add(scanner)
            }
            Reflections(Configuration(scanners = scanners.toSet()))
        }
    }

    override fun save(reflections: Reflections, file: File) = tryOrThrow("could not save to file $file") {
        file.makeParents()
        FileWriter(file).use { writer -> writeTo(writer, reflections) }
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
        reflections.scanners().forEach { scanner ->
            val indexElement = root.addElement(scanner.classToIndexName())
            scanner.store.map.entries.forEach { (key, values) ->
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

    private fun String.indexNameToClass() = Class.forName("${Scanner::class.java.`package`.name}.${this}")!!
    private fun Scanner.classToIndexName() = javaClass.simpleName!!
}
