package top.frankyang.unityfs4j.impl;

import top.frankyang.unityfs4j.engine.UnityClass;
import top.frankyang.unityfs4j.engine.UnityField;
import top.frankyang.unityfs4j.engine.UnityObject;

@UnityClass
public interface PPtr<T> extends UnityObject {
    @UnityField("m_FileID")
    int getFileId();

    @UnityField("m_PathID")
    long getPathId();

    default boolean isValid() {
        return !(getFileId() == 0 && getPathId() == 0);
    }

    default T getReferent() {
        if (!isValid()) return null;
        //noinspection unchecked
        return (T) getFields().computeIfAbsent("_referent", s ->
            getObjectInfo()
                .getAsset()
                .getRefs()
                .get(getFileId())
                .getReferent()
                .getObjects()
                .get(getPathId())
                .getObject()
        );
    }
}
