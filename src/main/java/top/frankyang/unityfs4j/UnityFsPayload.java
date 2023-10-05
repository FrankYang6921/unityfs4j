package top.frankyang.unityfs4j;

import lombok.Getter;
import top.frankyang.unityfs4j.UnityFsMetadata.DataBlock;
import top.frankyang.unityfs4j.UnityFsMetadata.DataNode;
import top.frankyang.unityfs4j.asset.Asset;
import top.frankyang.unityfs4j.io.AbstractRandomAccess;
import top.frankyang.unityfs4j.util.BufferUtils;
import top.frankyang.unityfs4j.util.CompressionUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Getter
public class UnityFsPayload extends AbstractRandomAccess implements Iterable<Asset> {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final Map<DataNode, Asset> nodeAssetMap = new HashMap<>();

    private final Map<DataBlock, byte[]> blockDataMap = new HashMap<>();

    private final ByteBuffer buffer;

    private final UnityFsMetadata metadata;

    private final UnityFsContext context;

    private final List<DataBlock> dataBlocks;

    private final int baseOffset;

    private final int actualSize;

    protected int ptr;

    protected DataBlock curBlock;

    protected ByteBuffer curBuffer;

    protected int curOffset;

    protected UnityFsPayload(ByteBuffer buffer, UnityFsMetadata metadata, UnityFsContext context) {
        this.buffer = buffer;
        this.metadata = metadata;
        this.context = context;
        dataBlocks = metadata.dataBlocks();
        baseOffset = buffer.position();
        actualSize = dataBlocks.stream().mapToInt(DataBlock::actualSize).sum();
        seek(0);
    }

    protected void seekToBlock(long offset) {
        int zippedOffset = 0;
        int actualOffset = 0;
        loop:
        {
            for (var block : dataBlocks) {
                if (actualOffset + block.actualSize() > offset) {
                    curBlock = block;
                    break loop;
                }
                zippedOffset += block.zippedSize();
                actualOffset += block.actualSize();
            }
            curBlock = null;
            curOffset = actualSize;
            curBuffer = EMPTY_BUFFER;
            return;
        }
        curOffset = actualOffset;
        buffer.position(this.baseOffset + zippedOffset);
        byte[] bytes = blockDataMap.computeIfAbsent(curBlock, cb ->
            CompressionUtils.decompress(
                BufferUtils.asInputStream(buffer),
                cb.actualSize(),
                cb.compression()
            )
        );
        curBuffer = ByteBuffer.wrap(bytes);
    }

    protected boolean shouldSeek(long offset) {
        if (curBlock == null) return true;
        var currentBlockMax = curOffset + curBlock.actualSize();
        return curOffset > offset || offset >= currentBlockMax;
    }

    @Override
    public void seek(long offset) {
        if (ptr == offset) return;
        ptr = (int) offset;
        if (shouldSeek(offset)) {
            seekToBlock(offset);
        }
        curBuffer.position((int) offset - curOffset);
    }

    @Override
    public long tell() {
        return ptr;
    }

    @Override
    public long size() {
        return actualSize;
    }

    @Override
    public int read() {
        if (shouldSeek(ptr)) {
            seekToBlock(ptr);
        }
        var ret = BufferUtils.read(curBuffer);
        if (ret >= 0) {
            ptr++;
        }
        return ret;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        int allRead = 0;
        while (len > 0 && ptr < actualSize) {
            if (shouldSeek(ptr)) {
                seekToBlock(ptr);
            }
            int read = BufferUtils.read(curBuffer, b, off, len);
            ptr += read;
            len -= read;
            allRead += read;
        }
        return allRead;
    }

    @Override
    public Iterator<Asset> iterator() {
        return new AssetIterator();
    }

    protected class AssetIterator implements Iterator<Asset> {
        final Iterator<DataNode> itr = metadata.dataNodes().iterator();

        @Override
        public boolean hasNext() {
            return itr.hasNext();
        }

        @Override
        public Asset next() {
            return nodeAssetMap.computeIfAbsent(itr.next(), node -> new Asset(UnityFsPayload.this, node));
        }
    }
}
