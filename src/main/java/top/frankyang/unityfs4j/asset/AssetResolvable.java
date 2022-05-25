package top.frankyang.unityfs4j.asset;

import java.io.IOException;

public interface AssetResolvable {
    Asset resolve() throws IOException;
}
