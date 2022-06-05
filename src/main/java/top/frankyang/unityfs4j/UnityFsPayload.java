package top.frankyang.unityfs4j;

import lombok.Getter;
import lombok.val;
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

    protected int currentBlockBaseOffset;

    protected UnityFsPayload(ByteBuffer buffer, UnityFsMetadata metadata, UnityFsContext context) {
        this.buffer = buffer;
        this.metadata = metadata;
        this.context = context;
        dataBlocks = metadata.getDataBlocks();
        baseOffset = buffer.position();
        actualLength = dataBlocks.stream().mapToInt(DataBlock::getUncompressedSize).sum();
        seek(0);
    }

    protected void seekToBlock(long offset) {
        int baseOffset = 0;
        int realOffset = 0;
        loop:
        {
            for (val block : dataBlocks) {
                if (realOffset + block.getUncompressedSize() > offset) {
                    currentBlock = block;
                    break loop;
                }
                baseOffset += block.getCompressedSize();
                realOffset += block.getUncompressedSize();
            }
            currentBlock = null;
            currentBuffer = EMPTY_BUFFER;
            return;
        }
        currentBlockBaseOffset = realOffset;
        buffer.position(this.baseOffset + baseOffset);
        byte[] bytes;
        if (blockDataMap.containsKey(currentBlock)) {
            bytes = blockDataMap.get(currentBlock);
        } else {
            bytes = CompressionUtils.decompress(
                BufferUtils.asInputStream(buffer),
                currentBlock.getUncompressedSize(),
                currentBlock.getCompressionType()
            );
            blockDataMap.put(currentBlock, bytes);
        }
        currentBuffer = ByteBuffer.wrap(bytes);
    }

    protected boolean isOutOfCurrentBlock(long offset) {
        if (currentBlock == null) return true;
        val currentBlockMax = currentBlockBaseOffset +
            currentBlock.getUncompressedSize();
        return currentBlockBaseOffset > offset || offset >= currentBlockMax;
    }

    @Override
    public void seek(long offset) {
        if (pointer == offset) return;
        pointer = (int) offset;
        if (isOutOfCurrentBlock(offset)) {
            seekToBlock(offset);
        }
        currentBuffer.position((int) offset - currentBlockBaseOffset);
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
        if (isOutOfCurrentBlock(pointer)) {
            seekToBlock(pointer);
        }
        val ret = BufferUtils.read(currentBuffer);
        if (ret < 0) {
            return ret;
        }
        pointer++;
        return ret;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (isOutOfCurrentBlock(pointer)) {
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
        final Iterator<DataNode> itr = metadata.getDataNodes().iterator();

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
