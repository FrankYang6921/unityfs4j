package top.frankyang.unityfs4j.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class RandomAccessFile extends AbstractRandomAccess implements RandomAccess {
    protected final java.io.RandomAccessFile raf;

    public RandomAccessFile(File file) throws FileNotFoundException {
        raf = new java.io.RandomAccessFile(file, "r");
    }

    @Override
    public void seek(long offset) throws IOException {
        raf.seek(offset);
    }

    @Override
    public long tell() throws IOException {
        return raf.getFilePointer();
    }

    @Override
    public long size() throws IOException {
        return raf.length();
    }

    @Override
    public int read() throws IOException {
        return raf.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return raf.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
