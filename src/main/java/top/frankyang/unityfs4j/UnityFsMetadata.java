package top.frankyang.unityfs4j;

import java.util.List;
import java.util.UUID;

public record UnityFsMetadata(UUID uuid, List<DataBlock> dataBlocks, List<DataNode> dataNodes) {
    public record DataBlock(int zippedSize, int actualSize, int flag) {
        public Compression compression() {
            return Compression.values()[flag & 0x3f];
        }
    }

    public record DataNode(long offset, long length, int status, String name) {
    }
}
