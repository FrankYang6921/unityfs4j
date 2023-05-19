package top.frankyang.unityfs4j;

import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.asset.Asset;
import top.frankyang.unityfs4j.exception.UnresolvedAssetException;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Getter
public class UnityFsContext implements Closeable {
    private final Map<Path, UnityFsStream> pathStreamMap = new ConcurrentHashMap<>();

    private final Map<String, UnityFsStream> nameStreamMap = new ConcurrentHashMap<>();

    private final Map<String, Asset> assets = new ConcurrentHashMap<>();

    private final Path rootPath;

    private boolean closed;

    public UnityFsContext(Path rootPath) throws IOException {
        this.rootPath = rootPath.toAbsolutePath();
        if (!Files.isDirectory(this.rootPath)) {
            throw new FileNotFoundException(rootPath + " does not exist or isn't a directory");
        }
    }

    public static UnityFsStream streamOf(Path path) throws IOException {
        return new UnityFsContext(path.getParent()).getStream(path.getFileName());
    }

    public Map<Path, UnityFsStream> getPathStreamMap() {
        return Collections.unmodifiableMap(pathStreamMap);
    }

    public Map<String, UnityFsStream> getNameStreamMap() {
        return Collections.unmodifiableMap(nameStreamMap);
    }

    public Map<String, Asset> getAssets() {
        return Collections.unmodifiableMap(assets);
    }

    public Set<UnityFsStream> findStreams(Predicate<Path> predicate) throws IOException {
        return Files.walk(rootPath)
            .filter(Files::isRegularFile)
            .filter(predicate)
            .map(this::getStream0)
            .collect(Collectors.toUnmodifiableSet());
    }

    @SneakyThrows
    private UnityFsStream getStream0(Path path) {
        return getStream(path);
    }

    public UnityFsStream getStream(Path path) throws IOException {
        ensureOpen();
        if (!path.isAbsolute()) {
            path = rootPath.resolve(path);
        }
        var ret = pathStreamMap.get(path);  // act-then-check
        if (ret != null) return ret;
        ret = new UnityFsStream(FileChannel.open(path), this);
        ret.load();
        var name = ret.getName().toLowerCase();
        UnityFsStream s;
        if ((s = pathStreamMap.putIfAbsent(path, ret)) != null) {  // Someone else already put one concurrently
            ret.close();
            return s;  // Let him do the assets
        }
        if ((s = nameStreamMap.putIfAbsent(name, ret)) != null) {  // Someone else already put one concurrently
            ret.close();
            return s;  // Let him do the assets
        }
        for (Asset asset : ret.getPayload()) {
            assets.put(asset.getName().toLowerCase(), asset);
        }
        return ret;
    }

    public Asset getAssetByName(String name) {
        ensureOpen();
        name = name.toLowerCase();
        var asset = assets.get(name);  // act-then-check
        if (asset != null) {
            return asset;
        }
        throw new UnresolvedAssetException(name);
    }

    public Asset getAssetByUri(URI uri) {
        ensureOpen();
        if (!"archive".equals(uri.getScheme())) {
            throw new UnresolvedAssetException(uri.toString());
        }
        var parts = uri.getPath().substring(1).toLowerCase().split("/");
        var streamName = parts[0];
        var assetName = parts[1];
        var stream = nameStreamMap.get(streamName);  // act-then-check
        if (stream == null) {
            throw new UnresolvedAssetException(uri.toString());
        }
        for (Asset asset : stream) {
            if (assetName.equalsIgnoreCase(asset.getName())) {
                return asset;
            }
        }
        throw new UnresolvedAssetException(uri.toString());
    }

    protected void ensureOpen() {
        if (closed) throw new IllegalStateException("closed");
    }

    @Override
    public void close() {
        closed = true;
        for (var stream : nameStreamMap.values()) {
            IOUtils.closeQuietly(stream);
        }
    }
}
