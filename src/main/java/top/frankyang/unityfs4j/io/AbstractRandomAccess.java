package top.frankyang.unityfs4j.io;

import lombok.experimental.Delegate;
import lombok.val;

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractRandomAccess implements RandomAccess {
    @Delegate(types = EndianDataInput.class)
    protected final EndianDataInputStream in;

    protected AbstractRandomAccess() {
        in = new EndianDataInputStream(new Input());
    }

    @Override
    public void align() throws IOException {
        val ptr = tell();
        val off = (ptr + 3) & -4;
        if (off > ptr) {
            seek(off - ptr, Whence.POINTER);
        }
    }

    @Override
    public void seek(long offset, Whence whence) throws IOException {
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
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public final InputStream asInputStream() {
        return in;
    }

    @Override
    public void close() throws IOException {
    }

    protected class Input extends InputStream {
        protected long mark = -1;

        @Override
        public int read() throws IOException {
            return AbstractRandomAccess.this.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return AbstractRandomAccess.this.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return AbstractRandomAccess.this.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            val ptr = tell();
            val off = Math.max(ptr + n, size());
            seek(off);
            return off - ptr;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.max(size() - tell(), Integer.MAX_VALUE);
        }

        @Override
        public void close() throws IOException {
            AbstractRandomAccess.this.close();
        }

        @Override
        public void mark(int readLimit) {
            try {
                mark = tell();
            } catch (IOException e) {
                mark = -1;
            }
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
