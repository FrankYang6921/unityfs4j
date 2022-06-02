package top.frankyang.unityfs4j.asset;

import top.frankyang.unityfs4j.io.RandomAccess;

public class ExposedObjectInfo extends ObjectInfo {
    protected ExposedObjectInfo(Asset asset) {
        super(asset);
    }

    @Override
    protected Object read(TypeTree typeTree, RandomAccess buf) {
        if ("exposedName".equals(typeTree.getName())) {
            buf.readInt();
            return "";
        }
        return super.read(typeTree, buf);
    }
}
