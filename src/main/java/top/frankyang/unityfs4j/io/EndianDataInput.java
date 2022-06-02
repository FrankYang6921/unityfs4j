package top.frankyang.unityfs4j.io;

import lombok.SneakyThrows;
import lombok.val;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.util.UUID;

public interface EndianDataInput extends DataInput {
    void setBigEndian(boolean bigEndian);

    @Override
    void readFully(byte[] b);

    @Override
    void readFully(byte[] b, int off, int len);

    @Override
    int skipBytes(int n);

    @Override
    boolean readBoolean();

    @Override
    byte readByte();

    @Override
    int readUnsignedByte();

    @Override
    short readShort();

    @Override
    int readUnsignedShort();

    @Override
    char readChar();

    @Override
    int readInt();

    @Override
    long readLong();

    @Override
    float readFloat();

    @Override
    double readDouble();

    @Override
    String readLine();

    @Override
    String readUTF();

    default long readUnsignedInt() {
        return Integer.toUnsignedLong(readInt());
    }

    @SneakyThrows
    default String readString() {
        val buf = new ByteArrayOutputStream();
        int b;
        while ((b = readUnsignedByte()) != 0) {
            buf.write(b);
        }
        return buf.toString("UTF-8");
    }

    default UUID readUuid() {
        return new UUID(readLong(), readLong());
    }
}
