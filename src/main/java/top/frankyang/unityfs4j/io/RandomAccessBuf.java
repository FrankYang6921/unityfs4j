package top.frankyang.unityfs4j.io;

import java.io.IOException;


public class RandomAccessBuf extends AbstractRandomAccess implements RandomAccess {
    protected final byte[] buf;

    protected int ptr;

    private boolean closed;

    public RandomAccessBuf(byte[] buf) {
        this.buf = buf;
    }

    protected void ensureOpen() throws IOException {
        if (closed) throw new IOException("closed");
    }

    @Override
    public void seek(long offset) throws IOException {
        ensureOpen();
        ptr = (int) offset;
    }

    @Override
    public long tell() throws IOException {
        ensureOpen();
        return ptr;
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        return buf.length;
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        return ptr < buf.length ? Byte.toUnsignedInt(buf[ptr++]) : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        len = Math.min(buf.length - ptr, len);
        if (len <= 0) return -1;
        System.arraycopy(buf, ptr, b, off, len);
        ptr += len;
        return len;
    }

    @Override
    public void close() {
        closed = true;
    }
}
