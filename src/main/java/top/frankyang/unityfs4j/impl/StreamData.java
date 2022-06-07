package top.frankyang.unityfs4j.impl;

import lombok.val;
import top.frankyang.unityfs4j.asset.Asset;
import top.frankyang.unityfs4j.engine.UnityField;
import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.util.BufferUtils;

public interface StreamData extends UnityObject {
    @UnityField("_asset")
    Asset getAsset();

    @UnityField("_asset")
    void setAsset(Asset asset);

    long getOffset();

    long getSize();

    String getPath();

    default byte[] getData() {
        val payload = getAsset().getPayload();
        payload.seek(getAsset().getOffset() + getOffset());
        return BufferUtils.read(payload, (int) getSize());
    }
}
