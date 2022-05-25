package top.frankyang.unityfs4j;

import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import top.frankyang.unityfs4j.asset.Asset;

import java.io.IOException;
import java.nio.file.Paths;

class UnityFsTest {
    @Test
    void test() throws IOException {
        @Cleanup val root = new UnityFsRoot(Paths.get("D:/Miscellaneous/Android"));
        val stream = root.loadStream(Paths.get("charpack/char_017_huang.ab"));
        val a = System.currentTimeMillis();
        for (Asset asset : stream.getPayload()) {
            asset.load();
            for (val object : asset.getObjects().values()) {
                object.getObject();
            }
        }
        val b = System.currentTimeMillis();
        System.out.println((b - a) / 1e3);
    }
}