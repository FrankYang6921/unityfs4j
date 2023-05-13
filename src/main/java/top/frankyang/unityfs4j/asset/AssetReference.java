package top.frankyang.unityfs4j.asset;

import lombok.Getter;


import java.util.UUID;

@Getter
public class AssetReference implements AssetResolvable {
    private final Asset asset;

    protected UUID uuid;

    protected int type;

    protected String assetPath;

    protected String filePath;

    protected Asset referent;  // Cached

    public AssetReference(Asset asset) {
        this.asset = asset;
        load();
    }

    protected void load() {
        var payload = asset.getPayload();
        assetPath = payload.readString();
        uuid = payload.readUuid();
        type = payload.readInt();
        filePath = payload.readString();
    }

    @Override
    public Asset getReferent() {
        if (referent == null) {
            referent = asset.resolveAsset(filePath);
        }
        return referent;
    }
}
