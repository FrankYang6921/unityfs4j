package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;
import lombok.var;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.BufferUtils;

import java.io.IOException;
import java.util.*;

@Getter
public class TypeMetadata {
    private final Asset asset;

    private final List<Integer> classIds = new ArrayList<>();

    private final Map<Integer, byte[]> hashes = new HashMap<>();

    private final Map<Integer, TypeTree> types = new HashMap<>();

    protected String engineVersion;

    protected TypeMetadata(Asset asset) {
        this.asset = asset;
    }

    public static TypeMetadata getInstance() {
        return TypeMetadataSingleton.INSTANCE;
    }

    public List<Integer> getClassIds() {
        return Collections.unmodifiableList(classIds);
    }

    public Map<Integer, byte[]> getHashes() {
        return Collections.unmodifiableMap(hashes);
    }

    public Map<Integer, TypeTree> getTypes() {
        return Collections.unmodifiableMap(types);
    }

    protected final void load() {
        load(asset.getPayload(), asset.formatVersion);
    }

    protected void load(RandomAccess payload, int formatVersion) {
        engineVersion = payload.readString();
        payload.readInt();  // Platform ID, unnecessary

        if (formatVersion >= 13) {
            val hasTypeTrees = payload.readBoolean();
            val typeCount = payload.readInt();

            for (int i = 0; i < typeCount; i++) {
                var classId = payload.readInt();

                if (formatVersion >= 17) {
                    payload.readByte();  // DK
                    val scriptId = payload.readShort();
                    if (classId == 114) {
                        classId = scriptId >= 0 ? -2 - scriptId : -1;
                    }
                }
                classIds.add(classId);
                byte[] hash;
                if (classId < 0) {
                    hash = BufferUtils.read(payload, 32);
                } else {
                    hash = BufferUtils.read(payload, 16);
                }
                hashes.put(classId, hash);

                if (hasTypeTrees) {
                    val tree = new TypeTree(formatVersion);
                    tree.load(payload);
                    types.put(classId, tree);
                }

                if (formatVersion >= 21) {
                    payload.readInt();  // DK
                }
            }
            return;
        }
        val fieldCount = payload.readInt();
        for (int i = 0; i < fieldCount; i++) {
            var classId = payload.readInt();
            val tree = new TypeTree(formatVersion);
            tree.load(payload);
            types.put(classId, tree);
        }
    }

    private static class TypeMetadataSingleton {
        static final TypeMetadata INSTANCE = new TypeMetadata(null);

        static {
            try {
                INSTANCE.load(RandomAccess.of(IOUtils.resourceToByteArray("/structs.dat")), 15);
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }
}
