package top.frankyang.unityfs4j.io;



import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractRandomAccess implements RandomAccess {
    protected final EndianDataInputStream in;

    protected AbstractRandomAccess() {
        in = new EndianDataInputStream(new Input());
    }

    @Override
    public RandomAccess align() {
        var ptr = tell();
        var off = (ptr + 3) & -4;
        if (off > ptr) {
            seek(off - ptr, Whence.POINTER);
        }
        return this;
    }

    @Override
    public void seek(long offset, Whence whence) {
        switch (whence) {
            case HEAD:
                seek(offset);
            case TAIL:
                seek(size() + offset);
            case POINTER:
                seek(tell() + offset);
        }
    }

    @Override
    public int read(byte[] b) {
        return read(b, 0, b.length);
    }

    @Override
    public final InputStream asInputStream() {
        return in;
    }

    @Override
    public void close() {
    }

    @Override
    public void setBigEndian(boolean bigEndian) {
        in.setBigEndian(bigEndian);
    }

    @Override
    public void readFully(byte[] b) {
        in.readFully(b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) {
        in.readFully(b, off, len);
    }

    @Override
    public int skipBytes(int n) {
        return in.skipBytes(n);
    }

    @Override
    public boolean readBoolean() {
        return in.readBoolean();
    }

    @Override
    public byte readByte() {
        return in.readByte();
    }

    @Override
    public int readUnsignedByte() {
        return in.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return in.readShort();
    }

    @Override
    public int readUnsignedShort() {
        return in.readUnsignedShort();
    }

    @Override
    public char readChar() {
        return in.readChar();
    }

    @Override
    public int readInt() {
        return in.readInt();
    }

    @Override
    public long readLong() {
        return in.readLong();
    }

    @Override
    public float readFloat() {
        return in.readFloat();
    }

    @Override
    public double readDouble() {
        return in.readDouble();
    }

    @Override
    public String readLine() {
        return in.readLine();
    }

    @Override
    public String readUTF() {
        return in.readUTF();
    }

    @Override
    public long readUnsignedInt() {
        return in.readUnsignedInt();
    }

    @Override
    public String readString() {
        return in.readString();
    }

    protected class Input extends InputStream {
        protected long mark = -1;

        @Override
        public int read() {
            return AbstractRandomAccess.this.read();
        }

        @Override
        public int read(byte[] b) {
            return AbstractRandomAccess.this.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return AbstractRandomAccess.this.read(b, off, len);
        }

        @Override
        public long skip(long n) {
            var ptr = tell();
            var off = Math.max(ptr + n, size());
            seek(off);
            return off - ptr;
        }

        @Override
        public int available() {
            return (int) Math.max(size() - tell(), Integer.MAX_VALUE);
        }

        @Override
        public void close() {
            AbstractRandomAccess.this.close();
        }

        @Override
        public void mark(int readLimit) {
            mark = tell();
        }

        @Override
        public void reset() throws IOException {
            if (mark < 0) throw new IOException("not marked");
            seek(mark);
        }

        @Override
        public boolean markSupported() {
            return true;
        }
    }
}
