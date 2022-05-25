package top.frankyang.unityfs4j.io;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.IOUtils;

import java.io.*;

public class EndianDataInputStream extends FilterInputStream implements EndianDataInput {
    private final byte[] readBuffer = new byte[8];

    @Getter
    @Setter
    private boolean bigEndian = true;

    public EndianDataInputStream(InputStream in) {
        super(in);
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        IOUtils.readFully(this, b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        IOUtils.readFully(this, b, off, len);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        return (int) IOUtils.skip(this, n);
    }

    @Override
    public boolean readBoolean() throws IOException {
        int ch = in.read();
        if (ch < 0)
            throw new EOFException();
        return ch != 0;
    }

    @Override
    public byte readByte() throws IOException {
        return (byte) readUnsignedByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        int ch = in.read();
        if (ch < 0)
            throw new EOFException();
        return ch;
    }

    @Override
    public short readShort() throws IOException {
        return (short) readUnsignedShort();
    }

    @Override
    public int readUnsignedShort() throws IOException {
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

    @Override
    public char readChar() throws IOException {
        return (char) readUnsignedShort();
    }

    @Override
    public int readInt() throws IOException {
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

    @Override
    public long readLong() throws IOException {
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

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public String readLine() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String readUTF() throws IOException {
        return DataInputStream.readUTF(this);
    }
}
