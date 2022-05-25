package top.frankyang.unityfs4j.io;

import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public interface RandomAccess extends EndianDataInput, Closeable {
    RandomAccess EMPTY = new RandomAccessBuf(new byte[0]);

    static RandomAccess of(byte[] b) {
        return new RandomAccessBuf(b);
    }

    static RandomAccess of(File file) throws IOException {
        return new RandomAccessFile(file);
    }

    static RandomAccess of(URL url) throws IOException {
        return of(IOUtils.toByteArray(url));
    }

    static RandomAccess of(Path path) throws IOException {  // TODO use SeekableByteChannel
        if (path.getFileSystem() == FileSystems.getDefault()) {
            return of(path.toFile());
        }
        return of(Files.readAllBytes(path));
    }

    void align() throws IOException;

    void seek(long offset) throws IOException;

    void seek(long offset, Whence whence) throws IOException;

    long tell() throws IOException;

    long size() throws IOException;

    int read() throws IOException;

    int read(byte[] b) throws IOException;

    int read(byte[] b, int off, int len) throws IOException;

    InputStream asInputStream() throws IOException;
}
