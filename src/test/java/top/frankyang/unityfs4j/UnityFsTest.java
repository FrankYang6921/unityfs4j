package top.frankyang.unityfs4j;

import org.junit.jupiter.api.Test;
import top.frankyang.unityfs4j.extract.ExtractionManager;
import top.frankyang.unityfs4j.extract.Extractor;

import java.io.IOException;
import java.nio.file.Path;

class UnityFsTest {
    @Test
    void test() throws IOException {
        var context = new UnityFsContext(Path.of("C:/Users/Frank/Downloads/arknights-hg-2081/assets/AB/Android"));
        ExtractionManager mgr = ExtractionManager.builder()
            .extractors(Extractor.DEFAULTS)
            .build();
        context.getStream(Path.of("arts/rglktopic.ab"));
        mgr.tryExtractAll(context, Path.of("C:/Users/Frank/Downloads/Extract"));
    }
}