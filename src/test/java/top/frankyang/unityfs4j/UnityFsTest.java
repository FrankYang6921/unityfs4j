package top.frankyang.unityfs4j;

import lombok.val;
import org.junit.jupiter.api.Test;
import top.frankyang.unityfs4j.asset.Asset;
import top.frankyang.unityfs4j.extract.ExtractionManager;

import java.io.IOException;
import java.nio.file.Paths;

class UnityFsTest {
    @Test
    void test() throws IOException {
        val manager = new ExtractionManager()
            .defaultExtractors();
        val stream = UnityFsContext.streamOf(Paths.get("D:/Miscellaneous/Android/charpack/char_017_huang.ab"));
        for (Asset asset : stream.getPayload()) {
            for (val objectInfo : asset) {
                manager.tryExtract(objectInfo.getObject(), Paths.get("D:/Miscellaneous/Extract/charpack/"));
            }
        }
    }
}