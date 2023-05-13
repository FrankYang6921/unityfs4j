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

    private final int actualLength;

    protected int pointer;

    protected DataBlock currentBlock;

    protected ByteBuffer currentBuffer;

    protected int currentOffset;

    protected UnityFsPayload(ByteBuffer buffer, UnityFsMetadata metadata, UnityFsContext context) {
        this.buffer = buffer;
        this.metadata = metadata;
        this.context = context;
        dataBlocks = metadata.dataBlocks();
        baseOffset = buffer.position();
        actualLength = dataBlocks.stream().mapToInt(DataBlock::zippedSize).sum();
        seek(0);
    }

    protected void seekToBlock(long offset) {
        int baseOffset = 0;
        int realOffset = 0;
        loop:
        {
            for (var block : dataBlocks) {
                if (realOffset + block.zippedSize() > offset) {
                    currentBlock = block;
                    break loop;
                }
                baseOffset += block.actualSize();
                realOffset += block.zippedSize();
            }
            currentBlock = null;
            currentBuffer = EMPTY_BUFFER;
            return;
        }
        currentOffset = realOffset;
        buffer.position(this.baseOffset + baseOffset);
        byte[] bytes;
        if (blockDataMap.containsKey(currentBlock)) {
            bytes = blockDataMap.get(currentBlock);
        } else {
            bytes = CompressionUtils.decompress(
                BufferUtils.asInputStream(buffer),
                currentBlock.zippedSize(),
                currentBlock.compression()
            );
            blockDataMap.put(currentBlock, bytes);
        }
        currentBuffer = ByteBuffer.wrap(bytes);
    }

    protected boolean shouldSeek(long offset) {
        if (currentBlock == null) return true;
        var currentBlockMax = currentOffset + currentBlock.zippedSize();
        return currentOffset > offset || offset >= currentBlockMax;
    }

    @Override
    public void seek(long offset) {
        if (pointer == offset) return;
        pointer = (int) offset;
        if (shouldSeek(offset)) {
            seekToBlock(offset);
        }
        currentBuffer.position((int) offset - currentOffset);
    }

    @Override
    public long tell() {
        return pointer;
    }

    @Override
    public long size() {
        return actualLength;
    }

    @Override
    public int read() {
        if (shouldSeek(pointer)) {
            seekToBlock(pointer);
        }
        var ret = BufferUtils.read(currentBuffer);
        if (ret >= 0) {
            pointer++;
        }
        return ret;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (shouldSeek(pointer)) {
            seekToBlock(pointer);
        }
        len = BufferUtils.read(currentBuffer, b, off, len);
        pointer += len;
        return len;
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
