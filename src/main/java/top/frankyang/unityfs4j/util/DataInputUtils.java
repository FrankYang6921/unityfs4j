package top.frankyang.unityfs4j.util;

import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.IOException;

@UtilityClass
public class DataInputUtils {
    public String readNullEndingString(DataInput in) throws IOException {
        val buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.readByte()) != 0) {
            buf.write(b);
        }
        return buf.toString("UTF-8");
    }
}
