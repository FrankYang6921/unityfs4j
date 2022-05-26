package top.frankyang.unityfs4j.io;

public class RandomAccessBuf extends AbstractRandomAccess implements RandomAccess {
    protected final byte[] buf;

    protected int ptr;

    public RandomAccessBuf(byte[] buf) {
        this.buf = buf;
    }

    @Override
    public void seek(long offset) {
        ptr = (int) offset;
    }

    @Override
    public long tell() {
        return ptr;
    }

    @Override
    public long size() {
        return buf.length;
    }

    @Override
    public int read() {
        return ptr < buf.length ? Byte.toUnsignedInt(buf[ptr++]) : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        len = Math.min(buf.length - ptr, len);
        if (len <= 0) return -1;
        System.arraycopy(buf, ptr, b, off, len);
        ptr += len;
        return len;
    }
}
