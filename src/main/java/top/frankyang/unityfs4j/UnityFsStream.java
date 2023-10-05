package top.frankyang.unityfs4j;

import lombok.Getter;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

@Getter
public class UnityFsStream implements Iterable<Asset>, Closeable {
    public static final String MAGIC_WORD = "UnityFS";

    private final UnityFsContext context;

    private final FileChannel channel;

    private final ByteBuffer buffer;

    protected UnityFsHeader header;

    protected UnityFsMetadata metadata;

    protected UnityFsPayload payload;

    protected String name;

    protected UnityFsStream(FileChannel channel, UnityFsContext context) throws IOException {
        this.channel = channel;
        this.context = context;
        buffer = channel.map(READ_ONLY, 0, channel.size()).order(BIG_ENDIAN);
    }

    public UnityFsPayload load() {
        var magicWord = BufferUtils.getString(buffer);
        if (!magicWord.equals(MAGIC_WORD)) {
            throw new DataFormatException(
                "illegal magic word: '" + MAGIC_WORD + "' expected, got '" + magicWord + '\''
            );
        }

        var fileVersion = buffer.getInt();
        var playerVersion = BufferUtils.getString(buffer);
        var engineVersion = BufferUtils.getString(buffer);

        var length = buffer.getLong();
        var zippedSize = buffer.getInt();
        var actualSize = buffer.getInt();
        var flag = buffer.getInt();

        header = new UnityFsHeader(
            fileVersion, playerVersion, engineVersion,
            length, zippedSize, actualSize, flag
        );

        int pointer = 0;
        if (header.eofMetadata()) {
            pointer = buffer.position();
            BufferUtils.seekTail(buffer, header.zippedSize());
        }
        var bytes = CompressionUtils.decompress(
            BufferUtils.asInputStream(buffer),
            header.actualSize(),
            header.compression()
        );
        if (header.eofMetadata()) {
            buffer.position(pointer);
        }

        var in = new EndianDataInputStream(new ByteArrayInputStream(bytes));
        var uuid = new UUID(in.readLong(), in.readLong());

        var blockCount = in.readInt();
        var blocks = new ArrayList<DataBlock>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            blocks.add(new DataBlock(
                in.readInt(), in.readInt(), in.readShort()
            ));
        }

        var nodeCount = in.readInt();
        var nodes = new ArrayList<DataNode>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            var offset = in.readLong();
            var size = in.readLong();
            var status = in.readInt();
            var name = in.readString();
            nodes.add(new DataNode(
                offset, size, status, name
            ));
        }

        name = nodes.get(0).name();
        return payload = new UnityFsPayload(buffer, metadata = new UnityFsMetadata(uuid, blocks, nodes), context);
    }

    @Override
    public Iterator<Asset> iterator() {
        if (payload == null) {
            throw new NotYetReadException("must be loaded before iterated over");
        }
        return payload.iterator();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
