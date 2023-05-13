package top.frankyang.unityfs4j;

public record UnityFsHeader(
    int fileVersion,
    String playerVersion,
    String engineVersion,
    long length,
    int zippedSize,
    int actualSize,
    int flag
) {
    public Compression compression() {
        return Compression.values()[flag & 0x3f];
    }

    public boolean eofMetadata() {
        return (flag & 0x80) > 0;
    }
}
