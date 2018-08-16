package org.reflections.vfs;

import com.google.common.collect.AbstractIterator;
import org.reflections.ReflectionsException;
import org.reflections.util.Utils;
import org.reflections.vfs.Vfs.Dir;
import org.reflections.vfs.Vfs.File;

import java.io.IOException;
import java.net.URL;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 *
 */
public class JarInputDir implements Dir {

    private final URL url;
    JarInputStream jarInputStream;
    long           cursor;
    long           nextCursor;

    public JarInputDir(URL url) {
        this.url = url;
    }

    @Override
    public String getPath() {
        return url.getPath();
    }

    @Override
    public Iterable<File> getFiles() {
        return () -> new AbstractIterator<File>() {

            {
                try {
                    jarInputStream = new JarInputStream(url.openConnection().getInputStream());
                } catch (Exception e) {
                    throw new ReflectionsException("Could not open url connection", e);
                }
            }

            @Override
            protected File computeNext() {
                while (true) {
                    try {
                        ZipEntry entry = jarInputStream.getNextJarEntry();
                        if (entry == null) {
                            return endOfData();
                        }

                        long size = entry.getSize();
                        if (size < 0) {
                            size = 0xffffffffL + size; //JDK-6916399
                        }
                        nextCursor += size;
                        if (!entry.isDirectory()) {
                            return new JarInputFile(entry, JarInputDir.this, cursor, nextCursor);
                        }
                    } catch (IOException e) {
                        throw new ReflectionsException("could not get next zip entry", e);
                    }
                }
            }
        };
    }

    @Override
    public void close() {
        Utils.close(jarInputStream);
    }
}
