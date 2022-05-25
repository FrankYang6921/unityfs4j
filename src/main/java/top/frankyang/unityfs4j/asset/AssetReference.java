package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;
import top.frankyang.unityfs4j.util.DataInputUtils;

import java.io.IOException;
import java.util.UUID;

@Getter
public class AssetReference implements AssetResolvable {
    private final Asset asset;

    protected UUID uuid;

    protected int type;

    protected String assetPath;

    protected String filePath;

    public AssetReference(Asset asset) {
        this.asset = asset;
    }

    protected void load() throws IOException {
        val payload = asset.getPayload();
        assetPath = DataInputUtils.readNullEndingString(payload);
        uuid = new UUID(payload.readLong(), payload.readLong());
        type = payload.readInt();
        filePath = DataInputUtils.readNullEndingString(payload);
        resolve();
    }

    @Override
    public Asset resolve() throws IOException {
        return asset.getAsset(filePath);
    }
}
