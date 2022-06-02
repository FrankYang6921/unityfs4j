package top.frankyang.unityfs4j;

import lombok.val;
import org.junit.jupiter.api.Test;
import top.frankyang.unityfs4j.asset.Asset;

import java.io.IOException;
import java.nio.file.Paths;

class UnityFsTest {
    @Test
    void test() throws IOException {
        val stream = UnityFsContext.streamOf(Paths.get("D:/Miscellaneous/Android/charpack/char_017_huang.ab"));
        for (Asset asset : stream.getPayload()) {
            for (val object : asset) {
                System.out.println(object.getObject());
            }
        }
    }
}