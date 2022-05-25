package top.frankyang.unityfs4j.asset;

import top.frankyang.unityfs4j.io.RandomAccess;

import java.io.IOException;

public class ExposedObjectInfo extends ObjectInfo {
    protected ExposedObjectInfo(Asset asset) {
        super(asset);
    }

    @Override
    protected Object readValue(TypeTree typeTree, RandomAccess buf) throws IOException {
        if ("exposedName".equals(typeTree.getName())) {
            buf.readInt();
            return "";
        }
        return super.readValue(typeTree, buf);
    }
}
