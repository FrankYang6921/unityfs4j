package top.frankyang.unityfs4j.extract;

import lombok.Builder;
import lombok.Singular;
import top.frankyang.unityfs4j.UnityFsContext;
import top.frankyang.unityfs4j.UnityFsPayload;
import top.frankyang.unityfs4j.asset.Asset;
import top.frankyang.unityfs4j.asset.ObjectInfo;
import top.frankyang.unityfs4j.engine.UnityObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Builder
public class ExtractionManager {
    @Singular
    private List<Extractor<?>> extractors;

    public boolean tryExtract(Object object, Path path) throws IOException {
        if (object instanceof UnityObject unityObject) {
            Files.createDirectories(path);
            for (Extractor<?> e : extractors) {
                //noinspection unchecked
                var extractor = (Extractor<UnityObject>) e;
                if (extractor.accepts(unityObject)) {
                    extractor.accept(unityObject, path);
                    return true;
                }
            }
            return false;
        }
        throw new UnsupportedOperationException("UnityObject instances only, not " + object);
    }

    public int tryExtractAll(Asset asset, Path path) throws IOException {
        int i = 0;
        for (ObjectInfo objectInfo : asset) {
            if (tryExtract(objectInfo.getObject(), path)) i++;
        }
        return i;
    }

    public int tryExtractAll(UnityFsPayload payload, Path path) throws IOException {
        int i = 0;
        for (Asset asset : payload) {
            i += tryExtractAll(asset, path);
        }
        return i;
    }

    public int tryExtractAll(UnityFsContext context, Path path) throws IOException {
        int i = 0;
        for (var entry : context.getPathStreamMap().entrySet()) {
            var p = path.resolve(context.getRootPath().relativize(entry.getKey()));
            i += tryExtractAll(entry.getValue().getPayload(), p);
        }
        return i;
    }
}
