package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;
import top.frankyang.unityfs4j.UnityFsMetadata.NodeMetadata;
import top.frankyang.unityfs4j.UnityFsPayload;
import top.frankyang.unityfs4j.UnityFsRoot;
import top.frankyang.unityfs4j.UnityFsStream;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

@Getter
public class Asset implements AssetResolvable, Iterable<ObjectInfo> {
    private final RandomAccess payload;

    private final UnityFsStream stream;

    private final UnityFsRoot root;

    private final TypeMetadata typeMetadata = new TypeMetadata(this);

    private final String name;

    private final long offset;

    private final List<Pair<Long, Integer>> adds = new ArrayList<>();

    private final List<AssetResolvable> refs = new ArrayList<>();

    private final Map<Integer, TypeTree> types = new HashMap<>();

    private final Map<Long, ObjectInfo> objects = new HashMap<>();

    protected boolean loaded;

    protected boolean longObjectId;

    protected int metadataSize;

    protected int contentSize;

    protected int format;

    protected int contentOffset;

    public Asset(URL url, UnityFsRoot root) throws IOException {
        this(RandomAccess.of(url), null, root, url.getFile(), 0);
    }

    public Asset(File file, UnityFsRoot root) throws IOException {
        this(RandomAccess.of(file), null, root, file.getName(), 0);
    }

    public Asset(Path path, UnityFsRoot root) throws IOException {
        this(RandomAccess.of(path), null, root, path.getFileName().toString(), 0);
    }

    public Asset(UnityFsPayload payload, NodeMetadata node) {
        this(payload, payload.getStream(), payload.getStream().getRoot(), node.getName(), node.getOffset());
    }

    protected Asset(RandomAccess payload, UnityFsStream stream, UnityFsRoot root, String name, long offset) {
        this.payload = payload;
        this.stream = stream;
        this.root = root;
        this.name = name;
        this.offset = offset;
        refs.add(this);
    }

    public boolean isResource() {
        return name.endsWith(".resource") || name.endsWith(".resS");
    }

    public void load() throws IOException {
        if (loaded) return;
        if (isResource()) {
            loaded = true;
            return;
        }
        payload.setBigEndian(true);
        payload.seek(offset);

        metadataSize = payload.readInt();
        contentSize = payload.readInt();
        format = payload.readInt();
        contentOffset = payload.readInt();

        payload.setBigEndian(format <= 9 || payload.readInt() != 0);

        typeMetadata.load();

        if (format >= 7 && format <= 13) {
            longObjectId = payload.readInt() != 0;
        }

        val objectCount = payload.readInt();
        for (int i = 0; i < objectCount; i++) {
            if (format >= 14) payload.align();
            val object = new ObjectInfo(this);
            object.load();
            register(object);
        }

        if (format >= 11) {
            val addCount = payload.readInt();
            for (int i = 0; i < addCount; i++) {
                if (format >= 14) payload.align();
                adds.add(Pair.of(readId(), payload.readInt()));
            }
        }

        if (format >= 6) {
            val refCount = payload.readInt();
            for (int i = 0; i < refCount; i++) {
                val ref = new AssetReference(this);
                ref.load();
                refs.add(ref);
            }
        }

        if (!payload.readString().isEmpty()) {  // DK
            throw new IOException();
        }

        loaded = true;
    }

    protected void register(ObjectInfo object) {
        if (typeMetadata.getTrees().containsKey(object.getTypeId())) {
            types.computeIfAbsent(object.getTypeId(), typeMetadata.getTrees()::get);
        } else if (!types.containsKey(object.getTypeId())) {
            types.put(object.getTypeId(),
                TypeMetadata.getInstance().getTrees()
                    .getOrDefault(object.getClassId(), null)
            );
        }
        if (objects.containsKey(object.getPathId())) {
            throw new IllegalStateException("Duplicate of object: " + object);
        }
        objects.put(object.getPathId(), object);
    }

    protected long readId(RandomAccess buf) throws IOException {
        return format >= 14 ? buf.readLong() : buf.readInt();
    }

    protected long readId() throws IOException {
        return readId(payload);
    }

    protected Asset getAsset(String path) throws IOException {
        if (root == null) return null;
        try {
            if (path.contains(":")) {
                return root.getAsset(new URI(path));
            }
            return root.getAssetByFileName(path);
        } catch (FileNotFoundException |
                 URISyntaxException e) {
            return null;
        }
    }

    @Override
    public Asset resolve() {
        return this;
    }

    @Override
    public Iterator<ObjectInfo> iterator() {
        return objects.values().iterator();
    }
}
