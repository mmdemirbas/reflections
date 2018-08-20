package org.reflections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.reflections.adapters.JavassistAdapter
import org.reflections.util.ClasspathHelper
import org.reflections.vfs.JarInputDir
import org.reflections.vfs.SystemDir
import org.reflections.vfs.Vfs
import org.reflections.vfs.Vfs.DefaultUrlTypes
import org.reflections.vfs.ZipDir
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.text.MessageFormat.format
import java.util.jar.JarFile

/**  */
class VfsTest {

    //
    private //damn
    val someJar: URL
        get() {
            val urls = ClasspathHelper.forClassLoader()
            for (url in urls) {
                if (!url.toExternalForm().contains("surefire") && url.toExternalForm().endsWith(".jar")) {
                    return url
                }
            }
            throw RuntimeException()
        }

    private val someDirectory: URL
        get() {
            try {
                return File(ReflectionsTest.userDir).toURI().toURL()
            } catch (e: MalformedURLException) {
                throw RuntimeException(e)
            }

        }

    @Test
    @Throws(Exception::class)
    fun allKindsOfShittyUrls() {
        val mdAdapter = JavassistAdapter()

        run {
            val jar1 = someJar
            assertTrue(jar1.toString().startsWith("file:"))
            assertTrue(jar1.toString().contains(".jar"))

            assertTrue(Vfs.DefaultUrlTypes.jarFile.matches(jar1))
            assertFalse(DefaultUrlTypes.jarUrl.matches(jar1))
            assertFalse(DefaultUrlTypes.directory.matches(jar1))

            val dir = DefaultUrlTypes.jarFile.createDir(jar1)
            var file: Vfs.File? = null
            for (f in dir!!.files) {
                if (f.relativePath!!.endsWith(".class")) {
                    file = f
                    break
                }
            }

            val stringCF = mdAdapter.getOrCreateClassObject(file!!).delegate
            val className = mdAdapter.getClassName(JavassistClassWrapper(stringCF))
        }

        run {
            val rtJarUrl = ClasspathHelper.forClass(String::class.java)
            assertTrue(rtJarUrl!!.toString().startsWith("jar:file:"))
            assertTrue(rtJarUrl.toString().contains(".jar!"))

            assertFalse(DefaultUrlTypes.jarFile.matches(rtJarUrl))
            assertTrue(DefaultUrlTypes.jarUrl.matches(rtJarUrl))
            assertFalse(DefaultUrlTypes.directory.matches(rtJarUrl))

            val dir = DefaultUrlTypes.jarUrl.createDir(rtJarUrl)
            var file: Vfs.File? = null
            for (f in dir!!.files) {
                if (f.relativePath == "java/lang/String.class") {
                    file = f
                    break
                }
            }

            val stringCF = mdAdapter.getOrCreateClassObject(file!!).delegate
            val className = mdAdapter.getClassName(JavassistClassWrapper(stringCF))
            assertTrue(className == "java.lang.String")
        }

        run {
            val thisUrl = ClasspathHelper.forClass(javaClass)
            assertTrue(thisUrl!!.toString().startsWith("file:"))
            assertFalse(thisUrl.toString().contains(".jar"))

            assertFalse(DefaultUrlTypes.jarFile.matches(thisUrl))
            assertFalse(DefaultUrlTypes.jarUrl.matches(thisUrl))
            assertTrue(DefaultUrlTypes.directory.matches(thisUrl))

            val dir = DefaultUrlTypes.directory.createDir(thisUrl)
            var file: Vfs.File? = null
            for (f in dir!!.files) {
                if (f.relativePath == "org/reflections/VfsTest.class") {
                    file = f
                    break
                }
            }

            val stringCF = mdAdapter.getOrCreateClassObject(file!!).delegate
            val className = mdAdapter.getClassName(JavassistClassWrapper(stringCF))
            assertTrue(className == javaClass.name)
        }
        // create a file, then delete it so we can treat as a non-existing directory
        val tempFile = File.createTempFile("nosuch", "dir")
        tempFile.delete()
        assertFalse(tempFile.exists())
        val dir = DefaultUrlTypes.directory.createDir(tempFile.toURL())!!
        assertNotNull(dir)
        assertFalse(dir.files.iterator().hasNext())
        assertNotNull(dir.path)
        assertNotNull(dir.toString())
        dir.close()

    }

    @Test
    fun dirWithSpaces() {
        val urls = ClasspathHelper.forPackage("dir+with spaces")
        assertFalse(urls.isEmpty())
        for (url in urls) {
            testVfsDir(url)
        }
    }

    @Test
    fun vfsFromJar() {
        testVfsDir(someJar)
    }

    @Test
    fun vfsFromDir() {
        testVfsDir(someDirectory)
    }

    @Test
    @Throws(MalformedURLException::class)
    fun vfsFromDirWithJarInName() {
        var tmpFolder = System.getProperty("java.io.tmpdir")
        tmpFolder = if (tmpFolder.endsWith(File.separator)) tmpFolder else tmpFolder + File.separator
        val dirWithJarInName = tmpFolder + "tony.jarvis"
        val newDir = File(dirWithJarInName)
        newDir.mkdir()

        try {
            val dir = Vfs.fromURL(URL(format("file:{0}", dirWithJarInName)))

            assertEquals(dirWithJarInName, dir.path)
            assertEquals(SystemDir::class.java, dir.javaClass)
        } finally {
            newDir.delete()
        }
    }

    @Test
    @Throws(MalformedURLException::class)
    fun vfsFromDirWithinAJarUrl() {
        val directoryInJarUrl = ClasspathHelper.forClass(String::class.java)
        assertTrue(directoryInJarUrl!!.toString().startsWith("jar:file:"))
        assertTrue(directoryInJarUrl.toString().contains(".jar!"))

        val directoryInJarPath = directoryInJarUrl.toExternalForm().replaceFirst("jar:".toRegex(), "")
        val start = directoryInJarPath.indexOf(":") + 1
        val end = directoryInJarPath.indexOf(".jar!") + 4
        val expectedJarFile = directoryInJarPath.substring(start, end)

        val dir = Vfs.fromURL(URL(directoryInJarPath))

        assertEquals(ZipDir::class.java, dir.javaClass)
        assertEquals(expectedJarFile, dir.path)
    }

    @Test
    @Throws(MalformedURLException::class)
    fun vfsFromJarFileUrl() {
        testVfsDir(URL("jar:file:" + someJar.path + "!/"))
    }

    @Test
    fun findFilesFromEmptyMatch() {
        val jar = someJar
        val files = Vfs.findFiles(listOf(jar)) { file -> true }
        assertNotNull(files)
        assertTrue(files.iterator().hasNext())
    }

    private fun testVfsDir(url: URL) {
        println("testVfsDir($url)")
        assertNotNull(url)

        val dir = Vfs.fromURL(url)
        assertNotNull(dir)

        val files = dir.files
        val first = files.iterator().next()
        assertNotNull(first)

        first.name
        try {
            first.openInputStream()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        dir.close()
    }

    @Test
    @Ignore
    @Throws(MalformedURLException::class)
    fun vfsFromHttpUrl() {
        Vfs.addDefaultURLTypes(object : Vfs.UrlType {
            override fun matches(url: URL): Boolean {
                return url.protocol == "http"
            }

            override fun createDir(url: URL): Vfs.Dir? {
                return HttpDir(url)
            }
        })

        testVfsDir(URL("http://mirrors.ibiblio.org/pub/mirrors/maven2/org/slf4j/slf4j-api/1.5.6/slf4j-api-1.5.6.jar"))
    }

    //this is just for the test...
    internal class HttpDir(url: URL) : Vfs.Dir {

        private val file = downloadTempLocally(url)
        private val zipDir = ZipDir(JarFile(file!!))
        override val path: String = url.toExternalForm()

        override val files: Sequence<Vfs.File>
            get() = zipDir.files

        override fun close() {
            file!!.delete()
        }

        @Throws(IOException::class)
        private fun downloadTempLocally(url: URL): java.io.File? {
            val connection = url.openConnection() as HttpURLConnection
            if (connection.responseCode == 200) {
                val temp = java.io.File.createTempFile("urlToVfs", "tmp")
                val out = FileOutputStream(temp)
                val `in` = DataInputStream(connection.inputStream)

                var len: Int
                val ch = ByteArray(1024)
                len = `in`.read(ch)
                while (len != -1) {
                    out.write(ch, 0, len)
                    len = `in`.read(ch)
                }

                connection.disconnect()
                return temp
            }

            return null
        }
    }

    @Test
    fun vfsFromJarWithInnerJars() {
        //todo?
    }

    @Test
    fun jarInputStream() {
        val javassistAdapter = JavassistAdapter()

        for (jar in ClasspathHelper.forClassLoader()) {
            try {
                for (file in JarInputDir(jar).files.take(5)) {
                    if (file.name.endsWith(".class")) {
                        val className = javassistAdapter.getClassName(javassistAdapter.getOrCreateClassObject(file))
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }
}