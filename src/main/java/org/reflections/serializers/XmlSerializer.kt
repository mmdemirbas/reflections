package org.reflections.serializers

import org.dom4j.DocumentFactory
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.reflections.scanners.CompositeScanner
import org.reflections.scanners.SimpleScanner
import org.reflections.util.tryOrThrow
import java.io.Reader
import java.io.StringWriter

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
object XmlSerializer : Serializer {
    // todo: basitlik açısından JAXB object'e dönüştürülebilir
    override fun read(reader: Reader): CompositeScanner {
        return tryOrThrow("could not read.") {
            val scanners = mutableListOf<SimpleScanner<*>>()
            SAXReader().read(reader).rootElement.elements().forEach { e1 ->
                val index = e1 as org.dom4j.Element
                val scanner =
                        Class.forName("${SimpleScanner::class.java.`package`.name}.${index.name}")!!.newInstance() as SimpleScanner<*>
                index.elements().forEach { e2 ->
                    val entry = e2 as org.dom4j.Element
                    val key = entry.element("key")
                    entry.element("values").elements().map { it as org.dom4j.Node }.forEach {
                        scanner.addEntry(key.text, it.text)
                    }
                }
                scanners.add(scanner)
            }
            CompositeScanner(scanners)
        }
    }

    override fun write(scanners: CompositeScanner, writer: Appendable) {
        val document = DocumentFactory.getInstance().createDocument()
        val root = document.addElement("Reflections")
        scanners.scanners.forEach { scanner ->
            val indexElement = root.addElement(scanner.javaClass.simpleName!!)
            scanner.stringEntries().forEach { (key, values) ->
                val entryElement = indexElement.addElement("entry")
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
