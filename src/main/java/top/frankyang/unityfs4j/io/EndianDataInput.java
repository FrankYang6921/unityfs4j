package top.frankyang.unityfs4j.io;

import java.io.DataInput;
import java.io.IOException;

public interface EndianDataInput extends DataInput {
    boolean isBigEndian();

    void setBigEndian(boolean bigEndian);

    default long readUnsignedInt() throws IOException {
        return Integer.toUnsignedLong(readInt());
    }
}
