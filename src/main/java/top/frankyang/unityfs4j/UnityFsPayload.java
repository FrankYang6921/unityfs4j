package top.frankyang.unityfs4j;

import lombok.Getter;
import lombok.val;
import org.apache.commons.compress.compressors.CompressorException;
import top.frankyang.unityfs4j.UnityFsMetadata.BlockMetadata;
import top.frankyang.unityfs4j.UnityFsMetadata.NodeMetadata;
import top.frankyang.unityfs4j.asset.Asset;
import top.frankyang.unityfs4j.io.AbstractRandomAccess;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.io.Whence;
import top.frankyang.unityfs4j.util.CompressionUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Getter
public class UnityFsPayload extends AbstractRandomAccess implements Iterable<Asset> {
    private final Map<NodeMetadata, Asset> assetCache = new HashMap<>();

    private final UnityFsStream stream;

    private final UnityFsMetadata metadata;

    private final List<BlockMetadata> blocks;

    private final long baseOffset;

    private final long maxOffset;

    private long pointer;

    protected BlockMetadata currentBlock;

    protected RandomAccess currentStream;

    protected long currentBlockBaseOffset;

    protected UnityFsPayload(UnityFsStream stream, UnityFsMetadata metadata) throws IOException {
        this.stream = stream;
        this.metadata = metadata;
        blocks = metadata.getBlockMetadataList();
        baseOffset = stream.tell();
        maxOffset = blocks.stream().mapToInt(BlockMetadata::getUncompressedSize).sum();
        seek(0);
    }

    @Override
    public void seek(long offset) throws IOException {
        if (pointer == offset) return;
        pointer = offset;
        if (isOutOfCurrentBlock(offset)) {
            seekToBlock(offset);
        }
        currentStream.seek(offset - currentBlockBaseOffset);
    }

    @Override
    public void seek(long offset, Whence whence) throws IOException {
        switch (whence) {
            case HEAD:
                seek(offset);
            case TAIL:
                seek(size() + offset);
            case POINTER:
                seek(tell() + offset);
        }
    }

    @Override
    public long tell() {
        return pointer;
    }

    @Override
    public long size() {
        return maxOffset;
    }

    public void seekToBlock(long offset) throws IOException {
        long baseOffset = 0;
        long realOffset = 0;
        loop:
        {
            for (val block : blocks) {
                if (realOffset + block.getUncompressedSize() > offset) {
                    currentBlock = block;
                    break loop;
                }
                baseOffset += block.getCompressedSize();
                realOffset += block.getUncompressedSize();
            }
            currentBlock = null;
            currentStream = RandomAccess.EMPTY;
            return;
        }
        currentBlockBaseOffset = realOffset;
        stream.seek(this.baseOffset + baseOffset);
        byte[] bytes;
        try {
            bytes = CompressionUtils.decompress(
                stream.asInputStream(),
                currentBlock.getUncompressedSize(),
                currentBlock.getCompressionType()
            );
        } catch (CompressorException e) {
            throw new IOException(e);
        }
        currentStream = RandomAccess.of(bytes);
    }

    public boolean isOutOfCurrentBlock(long offset) {
        if (currentBlock == null) return true;
        val currentBlockMax = currentBlockBaseOffset +
            currentBlock.getUncompressedSize();
        return currentBlockBaseOffset > offset || offset >= currentBlockMax;
    }

    @Override
    public int read() throws IOException {
        if (isOutOfCurrentBlock(pointer)) {
            seekToBlock(pointer);
        }
        pointer++;
        return currentStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (isOutOfCurrentBlock(pointer)) {
            seekToBlock(pointer);
        }
        len = currentStream.read(b, off, len);
        pointer += len;
        return len;
    }

    @Override
    public AssetIterator iterator() {
        return new AssetIterator();
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    public class AssetIterator implements Iterator<Asset> {
        final Iterator<NodeMetadata> itr = metadata.getNodeMetadataList().iterator();

        @Override
        public boolean hasNext() {
            return itr.hasNext();
        }

        @Override
        public Asset next() {
            return assetCache.computeIfAbsent(itr.next(), node ->
                new Asset(UnityFsPayload.this, node)
            );
        }
    }
}
