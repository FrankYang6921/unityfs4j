package top.frankyang.unityfs4j.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.Compression;
import top.frankyang.unityfs4j.exception.DataFormatException;

import java.io.IOException;
import java.io.InputStream;

import static org.apache.commons.compress.compressors.CompressorStreamFactory.*;

@UtilityClass
public class CompressionUtils {
    private final CompressorStreamFactory FACTORY = getSingleton();

    public byte[] decompress(InputStream in, int size, Compression compression) {
        try {
            return decompress0(in, size, compression);
        } catch (CompressorException | IOException e) {
            throw new DataFormatException(e);
        }
    }

    @SneakyThrows
    public InputStream decompress(InputStream in, Compression compression) {
        return switch (compression) {
            case NONE -> in;  // do nothing
            case LZMA -> FACTORY.createCompressorInputStream(LZMA, in);
            case LZ4, LZ4HC -> FACTORY.createCompressorInputStream(LZ4_BLOCK, in);
            default -> throw new UnsupportedOperationException("not implemented");
        };
    }

    private byte[] decompress0(InputStream in, int size, Compression compression) throws IOException, CompressorException {
        var buf = new byte[size];
        in = switch (compression) {
            case NONE -> in;  // do nothing
            case LZMA -> FACTORY.createCompressorInputStream(LZMA, in);
            case LZ4, LZ4HC -> FACTORY.createCompressorInputStream(LZ4_BLOCK, in);
            default -> throw new UnsupportedOperationException("not implemented");
        };
        IOUtils.readFully(in, buf);
        return buf;
    }
}
