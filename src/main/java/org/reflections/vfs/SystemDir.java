package org.reflections.vfs;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;
import org.reflections.vfs.Vfs.Dir;

import java.io.File;
import java.util.*;

/*
 * An implementation of {@link org.reflections.vfs.Vfs.Dir} for directory {@link java.io.File}.
 */
public class SystemDir implements Dir {

    private final File file;

    public SystemDir(File file) {
        if ((file != null) && (!file.isDirectory() || !file.canRead())) {
            throw new RuntimeException("cannot use dir " + file);
        }

        this.file = file;
    }

    @Override
    public String getPath() {
        if (file == null) {
            return "/NO-SUCH-DIRECTORY/";
        }
        return file.getPath().replace("\\", "/");
    }

    @Override
    public Iterable<Vfs.File> getFiles() {
        if ((file == null) || !file.exists()) {
            return Collections.emptyList();
        }
        return () -> new AbstractIterator<Vfs.File>() {
            final Stack<File> stack = new Stack<>();

            {
                stack.addAll(listFiles(file));
            }

            @Override
            protected Vfs.File computeNext() {
                while (!stack.isEmpty()) {
                    File file = stack.pop();
                    if (file.isDirectory()) {
                        stack.addAll(listFiles(file));
                    } else {
                        return new SystemFile(SystemDir.this, file);
                    }
                }

                return endOfData();
            }
        };
    }

    private static List<File> listFiles(File file) {
        File[] files = file.listFiles();

        return (files != null) ? Lists.newArrayList(files) : Lists.newArrayList();
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return getPath();
    }
}
