package top.frankyang.unityfs4j;

import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
public class UnityFsMetadata {
    UUID uuid;

    List<BlockMetadata> blockMetadataList;

    List<NodeMetadata> nodeMetadataList;

    @Value
    public static class BlockMetadata {
        int uncompressedSize;

        int compressedSize;

        int flag;

        public CompressionType getCompressionType() {
            return CompressionType.values()[flag & 0x3f];
        }
    }

    @Value
    public static class NodeMetadata {
        long offset;

        long size;

        int status;

        String name;
    }
}
