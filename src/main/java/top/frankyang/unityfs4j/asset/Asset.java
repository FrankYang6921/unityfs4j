package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.SneakyThrows;

import top.frankyang.unityfs4j.UnityFsContext;
import top.frankyang.unityfs4j.UnityFsMetadata.DataNode;
import top.frankyang.unityfs4j.UnityFsPayload;
import top.frankyang.unityfs4j.exception.DataFormatException;
import top.frankyang.unityfs4j.exception.ObjectRegistryException;
import top.frankyang.unityfs4j.exception.UnresolvedAssetException;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.LongIntegerPair;

import java.net.URI;
import java.util.*;

@Getter
public class Asset implements AssetResolvable, Iterable<ObjectInfo> {
    protected final ArrayList<LongIntegerPair> adds = new ArrayList<>();

    protected final ArrayList<AssetResolvable> refs = new ArrayList<>();

    protected final Map<Integer, UnityType> types = new HashMap<>();

    protected final Map<Long, ObjectInfo> objects = new LinkedHashMap<>();

    private final UnityFsPayload payload;

    private final UnityFsContext context;

    private final UnityTypes unityTypes = new UnityTypes(this);

    private final String name;

    private final long offset;

    protected boolean loaded;

    protected boolean loading;

    protected boolean longObjectId;

    protected int metadataLength;

    protected int contentLength;

    protected int formatVersion;

    protected int contentOffset;

    public Asset(UnityFsPayload payload, DataNode node) {
        this(payload, payload.getContext(), node.name(), node.offset());
    }

    protected Asset(UnityFsPayload payload, UnityFsContext context, String name, long offset) {
        this.payload = payload;
        this.context = context;
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

        var objectCount = payload.readInt();
        for (int i = 0; i < objectCount; i++) {
            if (formatVersion >= 14) {
                payload.align();
            }
            register(new ObjectInfo(this));
        }

        if (formatVersion >= 11) {
            var addCount = payload.readInt();
            adds.ensureCapacity(addCount);
            for (int i = 0; i < addCount; i++) {
                if (formatVersion >= 14) {
                    payload.align();
                }
                var add = new LongIntegerPair(
                    readId(), payload.readInt()
                );
                adds.add(add);
            }
        }

        if (formatVersion >= 6) {
            var refCount = payload.readInt();
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
    protected Asset resolveAsset(String path) {
        if (context == null) {
            throw new UnresolvedAssetException(path);
        }
        var uri = new URI(path);
        if (path.contains(":")) {
            return context.getAssetByUri(uri);
        }
        return context.getAssetByName(path);
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
