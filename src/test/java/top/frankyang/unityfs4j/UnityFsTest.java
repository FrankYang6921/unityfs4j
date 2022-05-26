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
        val stream = root.loadStream(Paths.get("audio/sound_beta_2/general.ab"));
        val a = System.nanoTime();
        for (Asset asset : stream.getPayload()) {
            asset.load();
            for (val object : asset) {
                object.getObject();
            }
        }
        val b = System.nanoTime();
        System.out.println((b - a) / 1e9);
    }
}