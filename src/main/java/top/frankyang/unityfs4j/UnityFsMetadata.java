package top.frankyang.unityfs4j;

import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
public class UnityFsMetadata {
    UUID uuid;

    List<DataBlock> dataBlocks;

    List<DataNode> dataNodes;

    @Value
    public static class DataBlock {
        int uncompressedSize;

        int compressedSize;

        int flag;

        public CompressionType getCompressionType() {
            return CompressionType.values()[flag & 0x3f];
        }
    }

    @Value
    public static class DataNode {
        long offset;

        long length;

        int status;

        String name;
    }
}
