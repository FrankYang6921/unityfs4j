package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;
import lombok.var;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.DataInputUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class TypeMetadata {
    private final Asset asset;

    private final List<Integer> classIds = new ArrayList<>();

    private final Map<Integer, byte[]> hashes = new HashMap<>();

    private final Map<Integer, TypeTree> trees = new HashMap<>();

    protected String engineVersion;

    protected TypeMetadata(Asset asset) {
        this.asset = asset;
    }

    public static TypeMetadata getInstance() {
        return TypeMetadataSingleton.INSTANCE;
    }

    protected void load() throws IOException {
        load(asset.getPayload(), asset.getFormat());
    }

    protected void load(RandomAccess payload, int format) throws IOException {
        engineVersion = DataInputUtils.readNullEndingString(payload);
        payload.readInt();  // Platform ID, unnecessary

        if (format >= 13) {
            val hasTypeTrees = payload.readBoolean();
            val typeCount = payload.readInt();

            for (int i = 0; i < typeCount; i++) {
                var classId = payload.readInt();

                if (format >= 17) {
                    payload.readByte();  // DK
                    val scriptId = payload.readShort();
                    if (classId == 114) {
                        classId = scriptId >= 0 ? -2 - scriptId : -1;
                    }
                }
                classIds.add(classId);
                byte[] hash;
                if (classId < 0) {
                    hash = IOUtils.readFully(payload.asInputStream(), 32);
                } else {
                    hash = IOUtils.readFully(payload.asInputStream(), 16);
                }
                hashes.put(classId, hash);

                if (hasTypeTrees) {
                    val tree = new TypeTree(format);
                    tree.load(payload);
                    trees.put(classId, tree);
                }

                if (format >= 21) {
                    payload.readInt();  // DK
                }
            }
            return;
        }
        val fieldCount = payload.readInt();
        for (int i = 0; i < fieldCount; i++) {
            var classId = payload.readInt();
            val tree = new TypeTree(format);
            tree.load(payload);
            trees.put(classId, tree);
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
