package org.reflections.vfs;

import org.reflections.vfs.Vfs.File;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;

/**
 *
 */
public class JarInputFile implements File {

    private final ZipEntry    entry;
    private final JarInputDir jarInputDir;
    private final long        fromIndex;
    private final long        endIndex;

    public JarInputFile(ZipEntry entry, JarInputDir jarInputDir, long cursor, long nextCursor) {
        this.entry = entry;
        this.jarInputDir = jarInputDir;
        fromIndex = cursor;
        endIndex = nextCursor;
    }

    @Override
    public String getName() {
        String name = entry.getName();
        return name.substring(name.lastIndexOf('/') + 1);
    }

    @Override
    public String getRelativePath() {
        return entry.getName();
    }

    @Override
    public InputStream openInputStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                if ((jarInputDir.cursor >= fromIndex) && (jarInputDir.cursor <= endIndex)) {
                    int read = jarInputDir.jarInputStream.read();
                    jarInputDir.cursor++;
                    return read;
                } else {
                    return -1;
                }
            }
        };
    }
}
