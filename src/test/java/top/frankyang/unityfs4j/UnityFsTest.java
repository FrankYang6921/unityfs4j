package top.frankyang.unityfs4j;

import lombok.val;
import org.junit.jupiter.api.Test;
import top.frankyang.unityfs4j.extract.ExtractionManager;
import top.frankyang.unityfs4j.extract.Extractor;

import java.io.IOException;
import java.nio.file.Path;

class UnityFsTest {
    @Test
    void test() throws IOException {
        val context = new UnityFsContext(Path.of("D:/Playground/assets/AB/Android/gamedata/excel/"));
        ExtractionManager mgr = ExtractionManager.builder()
            .extractors(Extractor.DEFAULTS)
            .build();
        context.findStreams(p -> p.toString().endsWith(".ab"));
        mgr.tryExtractAll(context, Path.of("D:/Playground/Extract"));
    }
}