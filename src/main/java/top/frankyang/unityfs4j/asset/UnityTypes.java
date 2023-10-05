package top.frankyang.unityfs4j.asset;

import lombok.Getter;


import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.BufferUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

@Getter
public class UnityTypes {
    protected final List<Integer> classIds = new ArrayList<>();

    protected final Map<Integer, BigInteger> hashes = new HashMap<>();

    protected final Map<Integer, UnityType> types = new HashMap<>();

    private final Asset asset;

    protected String engineVersion;

    protected UnityTypes(Asset asset) {
        this.asset = asset;
    }

    public static UnityTypes getInstance() {
        return Holder.INSTANCE;
    }

    public List<Integer> getClassIds() {
        return Collections.unmodifiableList(classIds);
    }

    public Map<Integer, BigInteger> getHashes() {
        return Collections.unmodifiableMap(hashes);
    }

    public Map<Integer, UnityType> getTypes() {
        return Collections.unmodifiableMap(types);
    }

    protected final void load() {
        load(asset.getPayload(), asset.getFormatVersion());
    }

    protected void load(RandomAccess payload, int formatVersion) {
        engineVersion = payload.readString();
        payload.readInt();  // Platform ID, unnecessary

        if (formatVersion >= 13) {
            loadNew(payload, formatVersion);
        } else {
            loadOld(payload, formatVersion);
        }
    }

    protected void loadNew(RandomAccess payload, int formatVersion) {
        var hasTypeTrees = payload.readBoolean();
        var typeCount = payload.readInt();

        for (int i = 0; i < typeCount; i++) {
            var classId = payload.readInt();

            if (formatVersion >= 17) {
                payload.readByte();  // DK
                var scriptId = payload.readShort();
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
            hashes.put(classId, new BigInteger(hash));

            if (hasTypeTrees) {
                var tree = new UnityType(formatVersion);
                tree.load(payload);
                types.put(classId, tree);
            }

            if (formatVersion >= 21) {
                payload.readInt();  // DK
            }
        }
    }

    protected void loadOld(RandomAccess payload, int formatVersion) {
        var fieldCount = payload.readInt();
        for (int i = 0; i < fieldCount; i++) {
            var classId = payload.readInt();
            var tree = new UnityType(formatVersion);
            tree.load(payload);
            types.put(classId, tree);
        }
    }

    private static class Holder {
        static final UnityTypes INSTANCE = new UnityTypes(null);

        static {
            try {
                INSTANCE.load(RandomAccess.of(IOUtils.resourceToByteArray("/structs.dat")), 15);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }
}
