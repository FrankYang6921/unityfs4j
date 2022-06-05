package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;
import top.frankyang.unityfs4j.UnityFsContext;
import top.frankyang.unityfs4j.UnityFsMetadata.DataNode;
import top.frankyang.unityfs4j.UnityFsPayload;
import top.frankyang.unityfs4j.exception.DataFormatException;
import top.frankyang.unityfs4j.exception.ObjectRegistryException;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.LongIntegerPair;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

@Getter
public class Asset implements AssetResolvable, Iterable<ObjectInfo> {
    private final RandomAccess payload;

    private final UnityFsContext root;

    private final UnityTypes unityTypes = new UnityTypes(this);

    private final String name;

    private final long offset;

    private final ArrayList<LongIntegerPair> adds = new ArrayList<>();

    private final ArrayList<AssetResolvable> refs = new ArrayList<>();

    private final Map<Integer, UnityType> types = new HashMap<>();

    private final Map<Long, ObjectInfo> objects = new HashMap<>();

    protected boolean loaded;

    protected boolean loading;

    protected boolean longObjectId;

    protected int metadataLength;

    protected int contentLength;

    protected int formatVersion;

    protected int contentOffset;

    public Asset(Path path, UnityFsContext root) throws IOException {
        this(RandomAccess.of(path), root, path.getFileName().toString(), 0);
    }

    public Asset(UnityFsPayload payload, DataNode node) {
        this(payload, payload.getContext(), node.getName(), node.getOffset());
    }

    protected Asset(RandomAccess payload, UnityFsContext root, String name, long offset) {
        this.payload = payload;
        this.root = root;
        this.name = name;
        this.offset = offset;
        refs.add(this);
    }

    public List<LongIntegerPair> getAdds() {
        ensureLoaded();
        return Collections.unmodifiableList(adds);
    }

    public List<AssetResolvable> getRefs() {
        ensureLoaded();
        return Collections.unmodifiableList(refs);
    }

    public Map<Integer, UnityType> getTypes() {
        ensureLoaded();
        return Collections.unmodifiableMap(types);
    }

    public Map<Long, ObjectInfo> getObjects() {
        ensureLoaded();
        return Collections.unmodifiableMap(objects);
    }

    public boolean isLongObjectId() {
        ensureLoaded();
        return longObjectId;
    }

    public int getMetadataLength() {
        ensureLoaded();
        return metadataLength;
    }

    public int getContentLength() {
        ensureLoaded();
        return contentLength;
    }

    public int getFormatVersion() {
        ensureLoaded();
        return formatVersion;
    }

    public int getContentOffset() {
        ensureLoaded();
        return contentOffset;
    }

    public boolean isResource() {
        return name.endsWith(".resource") || name.endsWith(".resS");
    }

    public synchronized void ensureLoaded() {
        if (loaded || loading) return;
        if (isResource()) {
            loaded = true;
            return;
        }
        try {
            loading = true;
            load();
        } finally {
            loading = false;
        }
        loaded = true;
    }

    public void load() {
        payload.setBigEndian(true);
        payload.seek(offset);

        metadataLength = payload.readInt();
        contentLength = payload.readInt();
        formatVersion = payload.readInt();
        contentOffset = payload.readInt();

        payload.setBigEndian(formatVersion <= 9 || payload.readInt() != 0);

        unityTypes.load();

        if (formatVersion >= 7 && formatVersion <= 13) {
            longObjectId = payload.readInt() != 0;
        }

        val objectCount = payload.readInt();
        for (int i = 0; i < objectCount; i++) {
            if (formatVersion >= 14) {
                payload.align();
            }
            register(new ObjectInfo(this));
        }

        if (formatVersion >= 11) {
            val addCount = payload.readInt();
            adds.ensureCapacity(addCount);
            for (int i = 0; i < addCount; i++) {
                if (formatVersion >= 14) {
                    payload.align();
                }
                val add = LongIntegerPair.of(
                    readId(), payload.readInt()
                );
                adds.add(add);
            }
        }

        if (formatVersion >= 6) {
            val refCount = payload.readInt();
            refs.ensureCapacity(refCount);
            for (int i = 0; i < refCount; i++) {
                refs.add(new AssetReference(this));
            }
        }

        if (!payload.readString().isEmpty()) {  // DK
            throw new DataFormatException();
        }
    }

    protected void register(ObjectInfo object) {
        if (unityTypes.getTypes().containsKey(object.getTypeId())) {
            types.computeIfAbsent(object.getTypeId(), unityTypes.getTypes()::get);
        } else {
            types.computeIfAbsent(object.getTypeId(), t ->
                UnityTypes.getInstance().getTypes().get(object.getClassId())
            );
        }
        if (objects.containsKey(object.getPathId())) {
            throw new ObjectRegistryException("Duplicate of object: " + object);
        }
        objects.put(object.getPathId(), object);
    }

    protected long readId() {
        return readId(payload);
    }

    protected long readId(RandomAccess buf) {
        return formatVersion >= 14 ? buf.readLong() : buf.readInt();
    }

    @SneakyThrows
    protected Asset getAsset(String file) {
        if (root == null) {
            return null;
        }
        val uri = new URI(file);
        if (file.contains(":")) {
            return root.getAssetByUri(uri);
        }
        return root.getAssetByName(file);
    }

    @Override
    public Asset getReferent() {
        return this;
    }

    @Override
    public Iterator<ObjectInfo> iterator() {
        ensureLoaded();
        return objects.values().iterator();
    }
}
