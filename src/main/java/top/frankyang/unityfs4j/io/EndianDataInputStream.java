package top.frankyang.unityfs4j.io;

import lombok.Setter;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.io.IOUtils;

import java.io.*;

public class EndianDataInputStream extends FilterInputStream implements EndianDataInput {
    private final byte[] readBuffer = new byte[8];

    @Setter
    private boolean bigEndian = true;

    public EndianDataInputStream(InputStream in) {
        super(in);
    }

    @SneakyThrows
    @Override
    public void readFully(byte[] b) {
        IOUtils.readFully(this, b);
    }

    @SneakyThrows
    @Override
    public void readFully(byte[] b, int off, int len) {
        IOUtils.readFully(this, b, off, len);
    }

    @SneakyThrows
    @Override
    public int skipBytes(int n) {
        return (int) IOUtils.skip(this, n);
    }

    @SneakyThrows
    @Override
    public boolean readBoolean() {
        int ch = in.read();
        if (ch < 0)
            throw new EOFException();
        return ch != 0;
    }

    @SneakyThrows
    @Override
    public byte readByte() {
        return (byte) readUnsignedByte();
    }

    @SneakyThrows
    @Override
    public int readUnsignedByte() {
        int ch = in.read();
        if (ch < 0)
            throw new EOFException();
        return ch;
    }

    @SneakyThrows
    @Override
    public short readShort() {
        return (short) readUnsignedShort();
    }

    @SneakyThrows
    @Override
    public int readUnsignedShort() {
        int ch1, ch2;
        if (bigEndian) {
            ch1 = in.read();
            ch2 = in.read();
        } else {
            ch2 = in.read();
            ch1 = in.read();
        }
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (ch1 << 8) + ch2;
    }

    @SneakyThrows
    @Override
    public char readChar() {
        return (char) readUnsignedShort();
    }

    @SneakyThrows
    @Override
    public int readInt() {
        int ch1, ch2, ch3, ch4;
        if (bigEndian) {
            ch1 = in.read();
            ch2 = in.read();
            ch3 = in.read();
            ch4 = in.read();
        } else {
            ch4 = in.read();
            ch3 = in.read();
            ch2 = in.read();
            ch1 = in.read();
        }
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4;
    }

    @SneakyThrows
    @Override
    public long readLong() {
        IOUtils.readFully(this, readBuffer);
        if (bigEndian)
            return ((long) readBuffer[0] << 56) +
                ((long) (readBuffer[1] & 255) << 48) +
                ((long) (readBuffer[2] & 255) << 40) +
                ((long) (readBuffer[3] & 255) << 32) +
                ((long) (readBuffer[4] & 255) << 24) +
                ((long) (readBuffer[5] & 255) << 16) +
                ((long) (readBuffer[6] & 255) << 8) +
                (long) (readBuffer[7] & 255);
        return ((long) readBuffer[7] << 56) +
            ((long) (readBuffer[6] & 255) << 48) +
            ((long) (readBuffer[5] & 255) << 40) +
            ((long) (readBuffer[4] & 255) << 32) +
            ((long) (readBuffer[3] & 255) << 24) +
            ((long) (readBuffer[2] & 255) << 16) +
            ((long) (readBuffer[1] & 255) << 8) +
            (long) (readBuffer[0] & 255);
    }

    @SneakyThrows
    @Override
    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    @SneakyThrows
    @Override
    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    @SneakyThrows
    @Override
    public String readLine() {
        val buf = new ByteArrayOutputStream();
        int b;
        while ((b = readUnsignedByte()) != '\n') {
            buf.write(b);
        }
        return buf.toString("UTF-8").replaceAll("\r$", "");
    }

    @SneakyThrows
    @Override
    public String readUTF() {
        return DataInputStream.readUTF(this);
    }
}
