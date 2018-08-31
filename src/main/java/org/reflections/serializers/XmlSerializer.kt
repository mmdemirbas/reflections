package org.reflections.serializers

import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.reflections.scanners.SimpleScanner
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
    // todo: basitlik açısından JAXB object'e dönüştürülebilir
    override fun read(inputStream: InputStream): List<SimpleScanner<*>> {
        return tryOrThrow("could not read.") {
            val scanners = mutableListOf<SimpleScanner<*>>()
            SAXReader().read(inputStream).rootElement.elements().forEach { e1 ->
                val index = e1 as org.dom4j.Element
                val scanner = index.name.indexNameToClass().newInstance() as SimpleScanner<*>
                index.elements().forEach { e2 ->
                    val entry = e2 as org.dom4j.Element
                    val key = entry.element("key")
                    entry.element("values").elements().map { it as org.dom4j.Node }.forEach {
                        scanner.store.put(Datum(key.text), Datum(it.text))
                    }
                }
                scanners.add(scanner)
            }
            scanners
        }
    }

    override fun save(scanners: List<SimpleScanner<*>>, file: File) = tryOrThrow("could not save to file $file") {
        file.makeParents()
        FileWriter(file).use { writer -> writeTo(writer, scanners) }
    }

    override fun toString(scanners: List<SimpleScanner<*>>) = StringWriter().use { writer ->
        writeTo(writer, scanners)
        writer.toString()
    }

    private fun writeTo(writer: Writer, scanners: List<SimpleScanner<*>>) {
        val xmlWriter = XMLWriter(writer, OutputFormat.createPrettyPrint())
        xmlWriter.write(createDocument(scanners))
        xmlWriter.close()
    }

    private fun createDocument(scanners: List<SimpleScanner<*>>): org.dom4j.Document {
        val document = org.dom4j.DocumentFactory.getInstance().createDocument()
        val root = document.addElement("Reflections")
        scanners.forEach { scanner ->
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

    private fun String.indexNameToClass() = Class.forName("${SimpleScanner::class.java.`package`.name}.${this}")!!
    private fun SimpleScanner<*>.classToIndexName() = javaClass.simpleName!!
}
