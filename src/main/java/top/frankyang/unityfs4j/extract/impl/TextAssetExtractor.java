package top.frankyang.unityfs4j.extract.impl;

import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.extract.Extractor;
import top.frankyang.unityfs4j.impl.TextAsset;
import top.frankyang.unityfs4j.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TextAssetExtractor implements Extractor<TextAsset> {
    @Override
    public boolean accepts(UnityObject object) {
        return object instanceof TextAsset;
    }

    @Override
    public void accept(TextAsset textAsset, Path path) throws IOException {
        Files.write(path.resolve(textAsset.getName()), StringUtils.bytesOrString(textAsset.getScript(), UTF_8));
    }
}
