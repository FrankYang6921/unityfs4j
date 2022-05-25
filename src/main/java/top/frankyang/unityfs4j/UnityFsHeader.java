package top.frankyang.unityfs4j;

import lombok.Value;

@Value
public class UnityFsHeader {
    int fileVersion;

    String playerVersion;

    String engineVersion;

    long size;

    int compressedMetadataSize;

    int uncompressedMetadataSize;

    int flag;

    public CompressionType getCompressionType() {
        return CompressionType.values()[flag & 0x3f];
    }

    public boolean isEOFMetadata() {
        return (flag & 0x80) > 0;
    }
}
