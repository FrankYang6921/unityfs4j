package top.frankyang.unityfs4j;

import lombok.Getter;
import lombok.val;
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
import java.util.HashMap;
import java.util.Map;

@Getter
public class UnityFsContext implements Closeable {
    private final Map<Path, UnityFsStream> pathStreamMap = new HashMap<>();

    private final Map<String, UnityFsStream> nameStreamMap = new HashMap<>();

    private final Map<String, Asset> assets = new HashMap<>();

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

    public UnityFsStream getStream(Path path) throws IOException {
        ensureOpen();
        if (!path.isAbsolute()) {
            path = rootPath.resolve(path);
        }
        if (pathStreamMap.containsKey(path)) {
            return pathStreamMap.get(path);
        }
        val ret = new UnityFsStream(FileChannel.open(path), this);
        ret.readHeader();
        ret.readMetadata();
        ret.readPayload();
        val name = ret.getName().toLowerCase();
        pathStreamMap.put(path, ret);
        nameStreamMap.put(name, ret);
        for (Asset asset : ret.getPayload()) {
            assets.put(asset.getName().toLowerCase(), asset);
        }
        return ret;
    }

    public Asset getAssetByName(String name) {
        ensureOpen();
        name = name.toLowerCase();
        if (!assets.containsKey(name)) {
            throw new UnresolvedAssetException(name);
        }
        return assets.get(name);
    }

    public Asset getAssetByUri(URI uri) {
        ensureOpen();
        if (!"archive".equals(uri.getScheme())) {
            throw new UnresolvedAssetException(uri.toString());
        }
        val parts = uri.getPath().substring(1).toLowerCase().split("/");
        val streamName = parts[0];
        val assetName = parts[1];
        if (!nameStreamMap.containsKey(streamName)) {
            throw new UnresolvedAssetException(uri.toString());
        }
        val stream = nameStreamMap.get(streamName);
        for (Asset asset : stream.getPayload()) {
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
        for (val stream : nameStreamMap.values()) {
            IOUtils.closeQuietly(stream);
        }
    }
}
