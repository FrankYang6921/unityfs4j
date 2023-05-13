package top.frankyang.unityfs4j.extract;

import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.extract.impl.TextAssetExtractor;
import top.frankyang.unityfs4j.extract.impl.Texture2DExtractor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public interface Extractor<T extends UnityObject> {
    List<Extractor<?>> DEFAULTS = Arrays.asList(
        new TextAssetExtractor(),
        new Texture2DExtractor()
    );

    boolean accepts(UnityObject object);

    void accept(T object, Path path) throws IOException;
}
