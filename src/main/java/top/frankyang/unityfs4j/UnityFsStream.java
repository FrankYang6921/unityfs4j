package top.frankyang.unityfs4j;

import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.val;
import org.apache.commons.compress.compressors.CompressorException;
import top.frankyang.unityfs4j.UnityFsMetadata.BlockMetadata;
import top.frankyang.unityfs4j.UnityFsMetadata.NodeMetadata;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.io.Whence;
import top.frankyang.unityfs4j.util.CompressionUtils;
import top.frankyang.unityfs4j.util.DataInputUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

@Getter
public class UnityFsStream implements RandomAccess {
    public static final String MAGIC_WORD = "UnityFS";

    @Delegate(types = RandomAccess.class)
    private final RandomAccess in;

    private final UnityFsRoot root;

    protected UnityFsHeader header;

    protected UnityFsMetadata metadata;

    protected UnityFsPayload payload;

    protected String name;

    public UnityFsStream(RandomAccess in) {
        this(in, null);
    }

    protected UnityFsStream(RandomAccess in, UnityFsRoot root) {
        this.in = in;
        this.root = root;
    }

    public UnityFsHeader readHeader() throws IOException {
        val magicWord = DataInputUtils.readNullEndingString(in);
        if (!magicWord.equals(MAGIC_WORD)) {
            throw new UnsupportedOperationException(
                "Illegal magic word: '" + MAGIC_WORD + "' expected, got '" + magicWord + '\''
            );
        }

        val fileVersion = in.readInt();
        val playerVersion = DataInputUtils.readNullEndingString(in);
        val engineVersion = DataInputUtils.readNullEndingString(in);

        val size = in.readLong();
        val compressedMetadataSize = in.readInt();
        val uncompressedMetadataSize = in.readInt();
        val flag = in.readInt();

        return header = new UnityFsHeader(
            fileVersion,
            playerVersion,
            engineVersion,
            size,
            compressedMetadataSize,
            uncompressedMetadataSize,
            flag
        );
    }

    public UnityFsMetadata readMetadata() throws IOException {
        if (header == null) {
            throw new IllegalStateException("Header must be read before reading metadata");
        }

        long pointer = 0;
        if (header.isEOFMetadata()) {
            pointer = in.tell();
            in.seek(-header.getCompressedMetadataSize(), Whence.TAIL);
        }
        byte[] bytes;
        try {
            bytes = CompressionUtils.decompress(
                in.asInputStream(), header.getUncompressedMetadataSize(), header.getCompressionType()
            );
        } catch (CompressorException e) {
            throw new IOException(e);
        }
        if (header.isEOFMetadata()) {
            in.seek(pointer);
        }

        val in = new DataInputStream(new ByteArrayInputStream(bytes));
        val uuid = new UUID(in.readLong(), in.readLong());

        val blockCount = in.readInt();
        val blockMetadataList = new ArrayList<BlockMetadata>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            val uncompressedSize = in.readInt();
            val compressedSize = in.readInt();
            val flag = in.readShort();
            blockMetadataList.add(new BlockMetadata(
                uncompressedSize, compressedSize, flag
            ));
        }

        val nodeCount = in.readInt();
        val nodeMetadataList = new ArrayList<NodeMetadata>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            val offset = in.readLong();
            val size = in.readLong();
            val status = in.readInt();
            val name = DataInputUtils.readNullEndingString(in);
            nodeMetadataList.add(new NodeMetadata(
                offset, size, status, name
            ));
        }

        name = nodeMetadataList.get(0).getName();
        return metadata = new UnityFsMetadata(
            uuid,
            blockMetadataList,
            nodeMetadataList
        );
    }

    public UnityFsPayload readPayload() throws IOException {
        if (metadata == null) {
            throw new IllegalStateException("Metadata must be read before reading payload");
        }
        return payload = new UnityFsPayload(this, metadata);
    }
}
