package com.mmdemirbas.reflections

import com.mmdemirbas.reflections.Vfs.defaultUrlTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class VfsTest {
    private fun getSomeJar(): URL = urlForClassLoader().firstOrNull { url ->
        !url.toExternalForm().contains("surefire") && url.toExternalForm().endsWith(".jar")
    } ?: throw RuntimeException()

    // todo: split these tests

    @Test
    fun allKindsOfShittyUrls() {
        run {
            val fileSystem = FileSystems.getDefault()
            val jar1 = getSomeJar()
            assertTrue(jar1.toString().startsWith("file:"))
            assertTrue(jar1.toString().contains(".jar"))

            assertNotNull(VfsUrlTypes.jarFile(jar1, fileSystem))
            assertNull(VfsUrlTypes.jarUrl(jar1, fileSystem))
            assertNull(VfsUrlTypes.directory(jar1, fileSystem))

            val dir = VfsUrlTypes.jarFile(jar1, fileSystem)
            var virtualFile: VirtualFile? = null
            for (f in dir!!.files()) {
                if (f.relativePath!!.endsWith(".class")) {
                    virtualFile = f
                    break
                }
            }

            val stringCF = createClassAdapter(virtualFile!!)
            val className = stringCF.name
        }

        run {
            val fileSystem = FileSystems.getDefault()
            val rtJarUrl = urlForClass(String::class.java)
            assertTrue(rtJarUrl!!.toString().startsWith("jar:file:"))
            assertTrue(rtJarUrl.toString().contains(".jar!"))

            assertNull(VfsUrlTypes.jarFile(rtJarUrl, fileSystem))
            assertNotNull(VfsUrlTypes.jarUrl(rtJarUrl, fileSystem))
            assertNull(VfsUrlTypes.directory(rtJarUrl, fileSystem))

            val dir = VfsUrlTypes.jarUrl(rtJarUrl, fileSystem)
            var virtualFile: VirtualFile? = null
            for (f in dir!!.files()) {
                if (f.relativePath == "java/lang/String.class") {
                    virtualFile = f
                    break
                }
            }

            val stringCF = createClassAdapter(virtualFile!!)
            val className = stringCF.name
            assertTrue(className == "java.lang.String")
        }

        run {
            val fileSystem = FileSystems.getDefault()
            val thisUrl = urlForClass(javaClass)
            assertTrue(thisUrl!!.toString().startsWith("file:"))
            assertFalse(thisUrl.toString().contains(".jar"))

            assertNull(VfsUrlTypes.jarFile(thisUrl, fileSystem))
            assertNull(VfsUrlTypes.jarUrl(thisUrl, fileSystem))
            assertNotNull(VfsUrlTypes.directory(thisUrl, fileSystem))

            val dir = VfsUrlTypes.directory(thisUrl, fileSystem)
            var virtualFile: VirtualFile? = null
            for (f in dir!!.files()) {
                if (f.relativePath == "org/reflections/VfsTest.class") {
                    virtualFile = f
                    break
                }
            }

            val stringCF = createClassAdapter(virtualFile!!)
            val className = stringCF.name
            assertTrue(className == javaClass.name)
        }

        val fileSystem = FileSystems.getDefault()
        // create a file, then delete it so we can treat as a non-existing directory
        val tempFile = createTempPath()
        tempFile.delete()
        assertFalse(tempFile.exists())
        VfsUrlTypes.directory(tempFile.toURL(), fileSystem)!!.use { dir ->
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
        testVfsDir(getSomeJar())
    }

    @Test
    fun vfsFromDir() {
        testVfsDir(userDir.toUri().toURL())
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
            val dir = Vfs.fromURL(url = URL("file:$dirWithJarInName"), fileSystem = fileSystem)
            assertEquals(fileSystem.getPath(dirWithJarInName), dir.path)
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
        assertEquals(fileSystem.getPath(expectedJarFile), dir.path)
    }

    @Test
    fun vfsFromJarFileUrl() {
        testVfsDir(URL("jar:file:${getSomeJar().path}!/"))
    }

    @Test
    fun findFilesFromEmptyMatch() {
        val fileSystem = FileSystems.getDefault()
        val jar = getSomeJar()
        val files = Vfs.findFiles(inUrls = listOf(jar), fileSystem = fileSystem).toList()
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
        defaultUrlTypes.add { url: URL, fileSystem: FileSystem ->
            mapIf(url.protocol == "http") {
                HttpDir(url, fileSystem)
            }
        }

        testVfsDir(URL("http://mirrors.ibiblio.org/pub/mirrors/maven2/org/slf4j/slf4j-api/1.5.6/slf4j-api-1.5.6.jar"))
    }

    //this is just for the test...
    class HttpDir(url: URL, fileSystem: FileSystem) : VirtualDir(fileSystem.getPath(url.toExternalForm())) {
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
                val className = createClassAdapter(it).name
            }
        }
    }
}
