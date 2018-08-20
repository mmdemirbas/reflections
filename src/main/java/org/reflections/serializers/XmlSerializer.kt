package org.reflections.serializers

import org.reflections.Reflections
import org.reflections.ReflectionsException
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.Utils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter

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
            val document = org.dom4j.io.SAXReader().read(inputStream)
            for (e1 in document.rootElement.elements()) {
                val index = e1 as org.dom4j.Element
                for (e2 in index.elements()) {
                    val entry = e2 as org.dom4j.Element
                    val key = entry.element("key")
                    val values = entry.element("values")
                    for (o3 in values.elements()) {
                        val value = o3 as org.dom4j.Node
                        reflections.store.getOrCreate(index.name).put(key.text, value.text)
                    }
                }
            }
        } catch (e: org.dom4j.DocumentException) {
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
            val xmlWriter =
                    org.dom4j.io.XMLWriter(FileOutputStream(file), org.dom4j.io.OutputFormat.createPrettyPrint())
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
            val xmlWriter = org.dom4j.io.XMLWriter(writer, org.dom4j.io.OutputFormat.createPrettyPrint())
            xmlWriter.write(document)
            xmlWriter.close()
            return writer.toString()
        } catch (e: IOException) {
            throw RuntimeException()
        }

    }

    private fun createDocument(reflections: Reflections): org.dom4j.Document {
        val map = reflections.store
        val document = org.dom4j.DocumentFactory.getInstance().createDocument()
        val root = document.addElement("Reflections")
        for (indexName in map.keySet()) {
            val indexElement = root.addElement(indexName)
            for (key in map[indexName].keySet()) {
                val entryElement = indexElement.addElement("entry")
                entryElement.addElement("key").text = key
                val valuesElement = entryElement.addElement("values")
                for (value in map[indexName].get(key)!!) {
                    valuesElement.addElement("value").text = value
                }
            }
        }
        return document
    }
}
