package org.reflections.serializers

import org.dom4j.*
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.reflections.Reflections
import org.reflections.ReflectionsException
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.Utils
import java.io.*

/**
 * serialization of Reflections to xml
 *
 *
 * an example of produced xml:
 * <pre>
 * &#60?xml version="1.0" encoding="UTF-8"?>
 *
 * &#60Reflections>
 * &#60SubTypesScanner>
 * &#60entry>
 * &#60key>com.google.inject.Module&#60/key>
 * &#60values>
 * &#60value>fully.qualified.name.1&#60/value>
 * &#60value>fully.qualified.name.2&#60/value>
 * ...
</pre> *
 */
class XmlSerializer : Serializer {

    override fun read(inputStream: InputStream): Reflections {
        var reflections: Reflections
        try {
            val constructor = Reflections::class.java.getDeclaredConstructor()
            constructor.isAccessible = true
            reflections = constructor.newInstance()
        } catch (e: Exception) {
            reflections = Reflections(ConfigurationBuilder())
        }

        try {
            val document = SAXReader().read(inputStream)
            for (e1 in document.rootElement.elements()) {
                val index = e1 as Element
                for (e2 in index.elements()) {
                    val entry = e2 as Element
                    val key = entry.element("key")
                    val values = entry.element("values")
                    for (o3 in values.elements()) {
                        val value = o3 as Node
                        reflections.store!!.getOrCreate(index.name).put(key.text, value.text)
                    }
                }
            }
        } catch (e: DocumentException) {
            throw ReflectionsException("could not read.", e)
        } catch (e: Throwable) {
            throw RuntimeException("Could not read. Make sure relevant dependencies exist on classpath.", e)
        }

        return reflections
    }

    override fun save(reflections: Reflections, filename: String): File {
        val file = Utils.prepareFile(filename)


        try {
            val document = createDocument(reflections)
            val xmlWriter = XMLWriter(FileOutputStream(file), OutputFormat.createPrettyPrint())
            xmlWriter.write(document)
            xmlWriter.close()
        } catch (e: IOException) {
            throw ReflectionsException("could not save to file $filename", e)
        } catch (e: Throwable) {
            throw RuntimeException("Could not save to file $filename. Make sure relevant dependencies exist on classpath.",
                                   e)
        }

        return file
    }

    override fun toString(reflections: Reflections): String {
        val document = createDocument(reflections)

        try {
            val writer = StringWriter()
            val xmlWriter = XMLWriter(writer, OutputFormat.createPrettyPrint())
            xmlWriter.write(document)
            xmlWriter.close()
            return writer.toString()
        } catch (e: IOException) {
            throw RuntimeException()
        }

    }

    private fun createDocument(reflections: Reflections): Document {
        val map = reflections.store!!

        val document = DocumentFactory.getInstance().createDocument()
        val root = document.addElement("Reflections")
        for (indexName in map.keySet()) {
            val indexElement = root.addElement(indexName)
            for (key in map.get(indexName).keySet()) {
                val entryElement = indexElement.addElement("entry")
                entryElement.addElement("key").text = key
                val valuesElement = entryElement.addElement("values")
                for (value in map.get(indexName).get(key)) {
                    valuesElement.addElement("value").text = value
                }
            }
        }
        return document
    }
}
