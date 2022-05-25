package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;
import top.frankyang.unityfs4j.io.RandomAccess;

import java.io.IOException;

@Getter
public class ObjectPtr implements ObjectHolder {
    private final TypeTree typeTree;

    private final Asset asset;

    protected int fileId;

    protected long pathId;

    public ObjectPtr(TypeTree typeTree, Asset asset) {
        this.typeTree = typeTree;
        this.asset = asset;
    }

    protected void load(RandomAccess buf) throws IOException {
        fileId = buf.readInt();
        pathId = asset.readId(buf);
    }

    public boolean isValid() {
        return !(fileId == 0 && pathId == 0);
    }

    public Asset getAsset() {
        try {
            return asset.getRefs().get(fileId).resolve();
        } catch (IOException e) {
            return null;
        }
    }

    public ObjectInfo getObjectInfo() {
        val asset = getAsset();
        if (asset == null) {
            return null;
        }
        return asset.getObjects().get(pathId);
    }

    @Override
    public Object getObject() {
        val info = getObjectInfo();
        if (info == null) {
            return null;
        }
        return info.getObject();
    }
}
