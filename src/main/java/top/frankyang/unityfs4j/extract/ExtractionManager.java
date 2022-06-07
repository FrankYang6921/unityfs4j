package top.frankyang.unityfs4j.extract;

import lombok.val;
import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.extract.impl.TextAssetExtractor;
import top.frankyang.unityfs4j.extract.impl.Texture2DExtractor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExtractionManager {
    private static final List<Extractor<?>> DEFAULTS = Arrays.asList(
        new TextAssetExtractor(),
        new Texture2DExtractor()
    );

    private final List<Extractor<?>> extractors = new ArrayList<>();

    public ExtractionManager() {
    }

    public ExtractionManager defaultExtractors() {
        extractors.addAll(DEFAULTS);
        return this;
    }

    public ExtractionManager extractor(Extractor<?> extractor) {
        extractors.add(extractor);
        return this;
    }

    public boolean tryExtract(Object object, Path path) throws IOException {
        if (!(object instanceof UnityObject)) {
            throw new UnsupportedOperationException("UnityObject instances only, not " + object);
        }
        val unityObject = (UnityObject) object;
        for (Extractor<?> e : extractors) {
            //noinspection unchecked
            val extractor = (Extractor<UnityObject>) e;
            if (extractor.accepts(unityObject)) {
                extractor.accept(unityObject, path);
                return true;
            }
        }
        return false;
    }

    public void extract(Object object, Path path) throws IOException {
        if (!tryExtract(object, path)) {
            throw new UnsupportedOperationException("Cannot extract: " + object);
        }
    }
}
