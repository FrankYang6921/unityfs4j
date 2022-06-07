package top.frankyang.unityfs4j.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.io.EndianDataInput;
import top.frankyang.unityfs4j.io.RandomAccess;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

@UtilityClass
public class BufferUtils {
    @SneakyThrows
    public String getString(ByteBuffer buffer) {
        val stream = new ByteArrayOutputStream();
        byte b;
        while ((b = buffer.get()) != 0) {
            stream.write(b);
        }
        return stream.toString("UTF-8");
    }

    public void seekTail(ByteBuffer buffer, int offset) {
        buffer.position(buffer.limit() - offset);
    }

    public int read(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        return buffer.get() & 0xff;
    }

    public int read(ByteBuffer buffer, byte[] b, int off, int len) {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        len = Math.min(len, buffer.remaining());
        buffer.get(b, off, len);
        return len;
    }

    public long skip(ByteBuffer buffer, long n) {
        if (!buffer.hasRemaining()) {
            return 0;
        }
        int len = (int) Math.min(n, buffer.remaining());
        buffer.position(buffer.position() + len);
        return len;
    }

    @SneakyThrows
    public byte[] read(RandomAccess buf, int size) {
        return IOUtils.readFully(buf.asInputStream(), size);
    }

    public boolean[] readBooleans(EndianDataInput buf, int size) {
        val result = new boolean[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.readBoolean();
        }
        return result;
    }

    public short[] readShorts(EndianDataInput buf, int size) {
        val result = new short[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.readShort();
        }
        return result;
    }

    public int[] readUnsignedShorts(EndianDataInput buf, int size) {
        val result = new int[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.readUnsignedShort();
        }
        return result;
    }

    public int[] readInts(EndianDataInput buf, int size) {
        val result = new int[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.readInt();
        }
        return result;
    }

    public long[] readUnsignedInts(EndianDataInput buf, int size) {
        val result = new long[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.readUnsignedInt();
        }
        return result;
    }

    public long[] readLongs(EndianDataInput buf, int size) {
        val result = new long[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.readLong();
        }
        return result;
    }

    public float[] readFloats(EndianDataInput buf, int size) {
        val result = new float[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.readFloat();
        }
        return result;
    }

    public double[] readDoubles(EndianDataInput buf, int size) {
        val result = new double[size];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.readDouble();
        }
        return result;
    }

    public InputStream asInputStream(ByteBuffer buffer) {
        return new ByteBufferInputStream(buffer);
    }

    final class ByteBufferInputStream extends InputStream {
        final ByteBuffer buf;

        ByteBufferInputStream(ByteBuffer buf) {
            this.buf = buf;
        }

        @Override
        public int read() {
            return BufferUtils.read(buf);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return BufferUtils.read(buf, b, off, len);
        }

        @Override
        public long skip(long n) {
            return BufferUtils.skip(buf, n);
        }

        @Override
        public int available() {
            return buf.remaining();
        }

        @Override
        public void mark(int readLimit) {
            buf.mark();
        }

        @Override
        public void reset() {
            buf.reset();
        }

        @Override
        public boolean markSupported() {
            return true;
        }
    }
}
