package top.frankyang.unityfs4j.extract.impl;

import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.extract.Extractor;
import top.frankyang.unityfs4j.impl.Texture2D;
import top.frankyang.unityfs4j.util.CodecUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Texture2DExtractor implements Extractor<Texture2D> {
    @Override
    public boolean accepts(UnityObject object) {
        return object instanceof Texture2D;
    }

    @Override
    public void accept(Texture2D texture2d, Path path) throws IOException {
        ImageDecoder decoder = switch (texture2d.getTextureFormat()) {
            case 3 -> CodecUtils::decodeRgb24;
            case 34 -> CodecUtils::decodeEtcRgb4;
            case 47 -> CodecUtils::decodeEtc2Rgba8;
            default -> throw new UnsupportedOperationException("texture format: " + texture2d.getTextureFormat());
        };
        var image = decoder.decode(texture2d.getData(), texture2d.getWidth(), texture2d.getHeight());
        ImageIO.write(image, "png", Files.newOutputStream(path.resolve(texture2d.getName() + ".png")));
    }

    @FunctionalInterface
    interface ImageDecoder {
        BufferedImage decode(byte[] data, int width, int height);
    }
}
