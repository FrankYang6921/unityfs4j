package top.frankyang.unityfs4j.extract;

import top.frankyang.unityfs4j.engine.UnityObject;

import java.io.IOException;
import java.nio.file.Path;

public interface Extractor<T extends UnityObject> {
    boolean accepts(UnityObject object);

    void accept(T object, Path path) throws IOException;
}
