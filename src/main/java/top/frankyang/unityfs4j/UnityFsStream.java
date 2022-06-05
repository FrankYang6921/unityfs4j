package top.frankyang.unityfs4j;

import lombok.Getter;
import lombok.val;
import top.frankyang.unityfs4j.UnityFsMetadata.DataBlock;
import top.frankyang.unityfs4j.UnityFsMetadata.DataNode;
import top.frankyang.unityfs4j.asset.Asset;
import top.frankyang.unityfs4j.exception.DataFormatException;
import top.frankyang.unityfs4j.exception.NotYetReadException;
import top.frankyang.unityfs4j.io.EndianDataInputStream;
import top.frankyang.unityfs4j.util.BufferUtils;
import top.frankyang.unityfs4j.util.CompressionUtils;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

@Getter
public class UnityFsStream implements Iterable<Asset>, Closeable {
    public static final String MAGIC_WORD = "UnityFS";

    private final UnityFsContext context;

    private final FileChannel channel;

    private final MappedByteBuffer buffer;

    protected UnityFsHeader header;

    protected UnityFsMetadata metadata;

    protected UnityFsPayload payload;

    protected String name;

    protected UnityFsStream(FileChannel channel, UnityFsContext context) throws IOException {
        this.channel = channel;
        this.context = context;
        buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        buffer.order(ByteOrder.BIG_ENDIAN);
    }

    public UnityFsHeader readHeader() {
        val magicWord = BufferUtils.getString(buffer);
        if (!magicWord.equals(MAGIC_WORD)) {
            throw new DataFormatException(
                "Illegal magic word: '" + MAGIC_WORD + "' expected, got '" + magicWord + '\''
            );
        }

        val fileVersion = buffer.getInt();
        val playerVersion = BufferUtils.getString(buffer);
        val engineVersion = BufferUtils.getString(buffer);

        val size = buffer.getLong();
        val compressedSize = buffer.getInt();
        val uncompressedSize = buffer.getInt();
        val flag = buffer.getInt();

        return header = new UnityFsHeader(
            fileVersion,
            playerVersion,
            engineVersion,
            size,
            compressedSize,
            uncompressedSize,
            flag
        );
    }

    public UnityFsMetadata readMetadata() {
        if (header == null) {
            throw new NotYetReadException("Header must be read before reading metadata");
        }

        int pointer = 0;
        if (header.isEOFMetadata()) {
            pointer = buffer.position();
            BufferUtils.seekTail(buffer, header.getCompressedSize());
        }
        byte[] bytes = CompressionUtils.decompress(
            BufferUtils.asInputStream(buffer),
            header.getUncompressedSize(),
            header.getCompressionType()
        );
        if (header.isEOFMetadata()) {
            buffer.position(pointer);
        }

        val in = new EndianDataInputStream(new ByteArrayInputStream(bytes));
        val uuid = new UUID(in.readLong(), in.readLong());

        val blockCount = in.readInt();
        val blocks = new ArrayList<DataBlock>(blockCount);  // Initial capacity given, avoid useless allocation
        for (int i = 0; i < blockCount; i++) {
            val uncompressedSize = in.readInt();
            val compressedSize = in.readInt();
            val flag = in.readShort();
            blocks.add(new DataBlock(
                uncompressedSize, compressedSize, flag
            ));
        }

        val nodeCount = in.readInt();
        val nodes = new ArrayList<DataNode>(nodeCount);  // Initial capacity given, avoid useless allocation
        for (int i = 0; i < nodeCount; i++) {
            val offset = in.readLong();
            val size = in.readLong();
            val status = in.readInt();
            val name = in.readString();
            nodes.add(new DataNode(
                offset, size, status, name
            ));
        }

        name = nodes.get(0).getName();
        return metadata = new UnityFsMetadata(
            uuid,
            blocks,
            nodes
        );
    }

    public UnityFsPayload readPayload() {
        if (metadata == null) {
            throw new NotYetReadException("Metadata must be read before reading payload");
        }
        return payload = new UnityFsPayload(buffer, metadata, context);
    }

    @Override
    public Iterator<Asset> iterator() {
        if (payload == null) {
            throw new NotYetReadException("Payload must be read before iterating assets");
        }
        return payload.iterator();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
