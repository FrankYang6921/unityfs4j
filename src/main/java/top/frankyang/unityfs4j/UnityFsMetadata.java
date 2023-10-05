package top.frankyang.unityfs4j;

import java.util.List;
import java.util.UUID;

public record UnityFsMetadata(UUID uuid, List<DataBlock> dataBlocks, List<DataNode> dataNodes) {
    public record DataBlock(int actualSize, int zippedSize, int flag) {
        public Compression compression() {
            return Compression.values()[flag & 0x3f];
        }
    }

    public record DataNode(long offset, long length, int status, String name) {
    }
}
