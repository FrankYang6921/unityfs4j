package top.frankyang.unityfs4j;

import lombok.Cleanup;
import lombok.Getter;
import lombok.val;
import lombok.var;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.asset.Asset;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.StringUtils;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Getter
public class UnityFsRoot implements Closeable {
    private final Map<Path, UnityFsStream> pathStreamMap = new HashMap<>();

    private final Map<String, UnityFsStream> nameStreamMap = new HashMap<>();

    private final Map<String, Asset> assets = new HashMap<>();

    private final Path rootPath;

    private boolean closed;

    public UnityFsRoot(Path rootPath) {
        this.rootPath = rootPath.toAbsolutePath();
    }

    public synchronized UnityFsStream loadStream(Path path) throws IOException {
        ensureOpen();
        path = rootPath.resolve(path);
        if (pathStreamMap.containsKey(path)) {
            return pathStreamMap.get(path);
        }
        val ret = new UnityFsStream(RandomAccess.of(path), this);
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

    public synchronized UnityFsStream discover(String name) throws IOException {
        name = name.toLowerCase();
        for (Path path : pathStreamMap.keySet()) {
            @Cleanup val stream = Files.walk(path.getParent(), 1);
            for (var itr = stream.iterator(); itr.hasNext(); ) {
                val child = itr.next();
                if (Files.isDirectory(child)) continue;
                val filename = StringUtils.getName(child);
                if (name.equals("cab-" + filename.toLowerCase())) {
                    return loadStream(child);
                }
            }
        }
        return null;
    }

    public synchronized Asset getAssetByFileName(String name) throws IOException {
        ensureOpen();
        if (!assets.containsKey(name)) {
            val path = rootPath.resolve(name);
            if (Files.exists(path)) {
                assets.put(name, new Asset(path, this));
            } else {
                discover(name);
            }
        }
        if (!assets.containsKey(name)) {
            throw new FileNotFoundException(name);
        }
        return assets.get(name);
    }

    public synchronized Asset getAsset(URI uri) throws IOException {
        if (!"archive".equals(uri.getScheme())) {
            throw new UnsupportedOperationException(uri.toString());
        }
        val parts = uri.getPath().toLowerCase().substring(1).split("/");
        val streamName = parts[0];
        val assetName = parts[1];
        if (!nameStreamMap.containsKey(streamName)) {
            discover(streamName);
            if (!nameStreamMap.containsKey(streamName)) {
                throw new FileNotFoundException(uri.toString());
            }
        }
        val stream = nameStreamMap.get(streamName);
        for (Asset asset : stream.getPayload()) {
            if (assetName.equals(asset.getName().toLowerCase())) {
                return asset;
            }
        }
        throw new FileNotFoundException(uri.toString());
    }

    protected void ensureOpen() throws IOException {
        if (closed) throw new IOException("closed");
    }

    @Override
    public synchronized void close() {
        closed = true;
        for (val stream : nameStreamMap.values()) {
            IOUtils.closeQuietly(stream);
        }
    }
}
