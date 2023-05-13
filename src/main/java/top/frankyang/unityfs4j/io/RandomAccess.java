package top.frankyang.unityfs4j.io;


import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface RandomAccess extends EndianDataInput, Closeable {
    static RandomAccess of(byte[] b) {
        return new RandomAccessImpl(ByteBuffer.wrap(b));
    }

    static RandomAccess of(byte[] b, int off, int len) {
        return new RandomAccessImpl(ByteBuffer.wrap(b, off, len));
    }

    static RandomAccess of(File file) throws IOException {
        return of(Paths.get(file.getPath()));
    }

    static RandomAccess of(Path path) throws IOException {
        return of(FileChannel.open(path));
    }

    static RandomAccess of(FileChannel channel) throws IOException {
        var buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        return new RandomAccessImpl(buf) {
            @Override
            public void close() {
                super.close();
                IOUtils.closeQuietly(channel);
            }
        };
    }

    RandomAccess align();

    void seek(long offset);

    void seek(long offset, Whence whence);

    long tell();

    long size();

    int read();

    int read(byte[] b);

    int read(byte[] b, int off, int len);

    InputStream asInputStream();

    @Override
    void close();
}
