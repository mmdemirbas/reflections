package org.reflections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.reflections.adapters.CreateJavassistClassAdapter
import org.reflections.util.createTempPath
import org.reflections.util.delete
import org.reflections.util.exists
import org.reflections.util.mkdir
import org.reflections.util.toURL
import org.reflections.util.urlForClass
import org.reflections.util.urlForClassLoader
import org.reflections.util.urlForPackage
import org.reflections.vfs.BuiltinVfsUrlTypes
import org.reflections.vfs.JarInputDir
import org.reflections.vfs.SystemDir
import org.reflections.vfs.Vfs
import org.reflections.vfs.Vfs.defaultUrlTypes
import org.reflections.vfs.VfsDir
import org.reflections.vfs.VfsFile
import org.reflections.vfs.VfsUrlType
import org.reflections.vfs.ZipDir
import java.io.DataInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.text.MessageFormat.format

class VfsTest {
    private val someJar: URL
        get() = urlForClassLoader().firstOrNull { url ->
            !url.toExternalForm().contains("surefire") && url.toExternalForm().endsWith(".jar")
        } ?: throw RuntimeException()

    private val someDirectory: URL get() = userDir.toUri().toURL()

    @Test
    fun allKindsOfShittyUrls() {
        val fileSystem = FileSystems.getDefault()

        run {
            val jar1 = someJar
            assertTrue(jar1.toString().startsWith("file:"))
            assertTrue(jar1.toString().contains(".jar"))

            assertNotNull(BuiltinVfsUrlTypes.JAR_FILE.createDir(jar1, fileSystem))
            assertNull(BuiltinVfsUrlTypes.JAR_URL.createDir(jar1, fileSystem))
            assertNull(BuiltinVfsUrlTypes.DIRECTORY.createDir(jar1, fileSystem))

            val dir = BuiltinVfsUrlTypes.JAR_FILE.createDir(jar1, fileSystem)
            var vfsFile: VfsFile? = null
            for (f in dir!!.files()) {
                if (f.relativePath!!.endsWith(".class")) {
                    vfsFile = f
                    break
                }
            }

            val stringCF = CreateJavassistClassAdapter(vfsFile!!)
            val className = stringCF.name
        }

        run {
            val rtJarUrl = urlForClass(String::class.java)
            assertTrue(rtJarUrl!!.toString().startsWith("jar:file:"))
            assertTrue(rtJarUrl.toString().contains(".jar!"))

            assertNull(BuiltinVfsUrlTypes.JAR_FILE.createDir(rtJarUrl, fileSystem))
            assertNotNull(BuiltinVfsUrlTypes.JAR_URL.createDir(rtJarUrl, fileSystem))
            assertNull(BuiltinVfsUrlTypes.DIRECTORY.createDir(rtJarUrl, fileSystem))

            val dir = BuiltinVfsUrlTypes.JAR_URL.createDir(rtJarUrl, fileSystem)
            var vfsFile: VfsFile? = null
            for (f in dir!!.files()) {
                if (f.relativePath == "java/lang/String.class") {
                    vfsFile = f
                    break
                }
            }

            val stringCF = CreateJavassistClassAdapter(vfsFile!!)
            val className = stringCF.name
            assertTrue(className == "java.lang.String")
        }

        run {
            val thisUrl = urlForClass(javaClass)
            assertTrue(thisUrl!!.toString().startsWith("file:"))
            assertFalse(thisUrl.toString().contains(".jar"))

            assertNull(BuiltinVfsUrlTypes.JAR_FILE.createDir(thisUrl, fileSystem))
            assertNull(BuiltinVfsUrlTypes.JAR_URL.createDir(thisUrl, fileSystem))
            assertNotNull(BuiltinVfsUrlTypes.DIRECTORY.createDir(thisUrl, fileSystem))

            val dir = BuiltinVfsUrlTypes.DIRECTORY.createDir(thisUrl, fileSystem)
            var vfsFile: VfsFile? = null
            for (f in dir!!.files()) {
                if (f.relativePath == "org/reflections/VfsTest.class") {
                    vfsFile = f
                    break
                }
            }

            val stringCF = CreateJavassistClassAdapter(vfsFile!!)
            val className = stringCF.name
            assertTrue(className == javaClass.name)
        }
        // create a file, then delete it so we can treat as a non-existing directory
        val tempFile = createTempPath()
        tempFile.delete()
        assertFalse(tempFile.exists())
        BuiltinVfsUrlTypes.DIRECTORY.createDir(tempFile.toURL(), fileSystem)!!.use { dir ->
            assertNotNull(dir)
            assertFalse(dir.files().iterator().hasNext())
            assertNotNull(dir.path)
            assertNotNull(dir.toString())
        }
    }

    @Test
    fun dirWithSpaces() {
        val urls = urlForPackage("dir+with spaces")
        assertFalse(urls.isEmpty())
        urls.forEach { url -> testVfsDir(url) }
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
    fun vfsFromDirWithJarInName() {
        val fileSystem = FileSystems.getDefault()
        var tmpFolder = System.getProperty("java.io.tmpdir")
        tmpFolder = if (tmpFolder.endsWith(fileSystem.separator)) tmpFolder else "$tmpFolder${fileSystem.separator}"
        val dirWithJarInName = "${tmpFolder}tony.jarvis"
        val newDir = fileSystem.getPath(dirWithJarInName)
        newDir.mkdir()

        try {
            val dir = Vfs.fromURL(url = URL(format("file:{0}", dirWithJarInName)), fileSystem = fileSystem)

            assertEquals(dirWithJarInName, dir.path)
            assertEquals(SystemDir::class.java, dir.javaClass)
        } finally {
            newDir.delete()
        }
    }

    @Test
    fun vfsFromDirWithinAJarUrl() {
        val fileSystem = FileSystems.getDefault()
        val directoryInJarUrl = urlForClass(String::class.java)
        assertTrue(directoryInJarUrl!!.toString().startsWith("jar:file:"))
        assertTrue(directoryInJarUrl.toString().contains(".jar!"))

        val directoryInJarPath = directoryInJarUrl.toExternalForm().replaceFirst("jar:".toRegex(), "")
        val start = directoryInJarPath.indexOf(":") + 1
        val end = directoryInJarPath.indexOf(".jar!") + 4
        val expectedJarFile = directoryInJarPath.substring(start, end)

        val dir = Vfs.fromURL(url = URL(directoryInJarPath), fileSystem = fileSystem)

        assertEquals(ZipDir::class.java, dir.javaClass)
        assertEquals(expectedJarFile, dir.path)
    }

    @Test
    fun vfsFromJarFileUrl() {
        testVfsDir(URL("jar:file:${someJar.path}!/"))
    }

    @Test
    fun findFilesFromEmptyMatch() {
        val fileSystem = FileSystems.getDefault()
        val jar = someJar
        val files = Vfs.findFiles(inUrls = listOf(jar), fileSystem = fileSystem) { file -> true }
        assertNotNull(files)
        assertTrue(files.iterator().hasNext())
    }

    private fun testVfsDir(url: URL) {
        val fileSystem = FileSystems.getDefault()

        println("testVfsDir($url)")
        assertNotNull(url)

        Vfs.fromURL(url = url, fileSystem = fileSystem).use { dir ->
            assertNotNull(dir)

            val files = dir.files()
            val first = files.iterator().next()
            assertNotNull(first)

            // todo: bu başı boş gezen ifadeler neye yarıyor? assertion falan yapılsın bari
            first.name
            first.openInputStream()
        }
    }

    @Test
    @Disabled
    fun vfsFromHttpUrl() {
        defaultUrlTypes.add(object : VfsUrlType {
            override fun createDir(url: URL, fileSystem: FileSystem) =
                    if (url.protocol == "http") HttpDir(url, fileSystem) else null
        })

        testVfsDir(URL("http://mirrors.ibiblio.org/pub/mirrors/maven2/org/slf4j/slf4j-api/1.5.6/slf4j-api-1.5.6.jar"))
    }

    //this is just for the test...
    class HttpDir(url: URL, fileSystem: FileSystem) : VfsDir(fileSystem.getPath(url.toExternalForm())) {
        private val file = downloadTempLocally(url, fileSystem)
        private val zipDir = ZipDir(file!!)
        override fun files() = zipDir.files()
        override fun close() = file!!.delete()

        private fun downloadTempLocally(url: URL, fileSystem: FileSystem): Path? {
            val connection = url.openConnection() as HttpURLConnection
            if (connection.responseCode == 200) {
                val temp = createTempPath()
                val out = Files.newOutputStream(temp)
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
        val fileSystem = FileSystems.getDefault()
        urlForClassLoader().forEach { jar ->
            JarInputDir(jar, fileSystem).files().take(5).filter { it.name.endsWith(".class") }.forEach {
                val className = CreateJavassistClassAdapter(it).name
            }
        }
    }
}
