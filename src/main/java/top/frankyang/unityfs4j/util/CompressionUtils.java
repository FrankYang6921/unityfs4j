package top.frankyang.unityfs4j.util;

import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.CompressionType;

import java.io.IOException;
import java.io.InputStream;

@UtilityClass
public class CompressionUtils {
    private final CompressorStreamFactory FACTORY = CompressorStreamFactory.getSingleton();

    public byte[] decompress(InputStream in, int size, CompressionType compressionType) throws IOException, CompressorException {
        val buf = new byte[size];
        switch (compressionType) {
            case NONE:
                break;
            case LZMA:
                in = FACTORY.createCompressorInputStream(CompressorStreamFactory.LZMA, in);
                break;
            case LZ4:
            case LZ4HC:
                in = FACTORY.createCompressorInputStream(CompressorStreamFactory.LZ4_BLOCK, in);
                break;
            default:
                throw new UnsupportedOperationException("Compression: " + compressionType);
        }
        IOUtils.readFully(in, buf);
        return buf;
    }
}
