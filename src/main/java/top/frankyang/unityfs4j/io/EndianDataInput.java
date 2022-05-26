package top.frankyang.unityfs4j.io;

import lombok.val;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.IOException;

public interface EndianDataInput extends DataInput {
    boolean isBigEndian();

    void setBigEndian(boolean bigEndian);

    default long readUnsignedInt() throws IOException {
        return Integer.toUnsignedLong(readInt());
    }

    default String readString() throws IOException {
        val buf = new ByteArrayOutputStream();
        int b;
        while ((b = readByte()) != 0) {
            buf.write(b);
        }
        return buf.toString("UTF-8");
    }
}
