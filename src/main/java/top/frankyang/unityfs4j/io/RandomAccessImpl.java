package top.frankyang.unityfs4j.io;

import top.frankyang.unityfs4j.util.BufferUtils;

import java.nio.ByteBuffer;

public class RandomAccessImpl extends AbstractRandomAccess implements RandomAccess {
    protected final ByteBuffer buf;

    protected RandomAccessImpl(ByteBuffer buf) {
        this.buf = buf;
    }

    @Override
    public void seek(long offset) {
        buf.position((int) offset);
    }

    @Override
    public long tell() {
        return buf.position();
    }

    @Override
    public long size() {
        return buf.limit();
    }

    @Override
    public int read() {
        return BufferUtils.read(buf);
    }

    @Override
    public int read(byte[] b, int off, int len) {
        return BufferUtils.read(buf, b, off, len);
    }
}
