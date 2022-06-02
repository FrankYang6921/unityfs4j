package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;

import java.util.UUID;

@Getter
public class AssetReference implements AssetResolvable {
    private final Asset asset;

    protected UUID uuid;

    protected int type;

    protected String assetPath;

    protected String filePath;

    protected Asset referent;

    public AssetReference(Asset asset) {
        this.asset = asset;
    }

    protected void load() {
        val payload = asset.getPayload();
        assetPath = payload.readString();
        uuid = payload.readUuid();
        type = payload.readInt();
        filePath = payload.readString();
    }

    @Override
    public Asset getReferent() {
        if (referent == null) {
            referent = asset.getAsset(filePath);
        }
        return referent;
    }
}
