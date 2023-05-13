package top.frankyang.unityfs4j.extract.impl;

import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.extract.Extractor;
import top.frankyang.unityfs4j.impl.Texture2D;
import top.frankyang.unityfs4j.util.CodecUtils;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Texture2DExtractor implements Extractor<Texture2D> {
    @Override
    public boolean accepts(UnityObject object) {
        return object instanceof Texture2D;
    }

    @Override
    public void accept(Texture2D texture2D, Path path) throws IOException {
        var image = switch (texture2D.getTextureFormat()) {
            case 3 -> CodecUtils.decodeRgb24(texture2D.getData(), texture2D.getWidth(), texture2D.getHeight());
            case 34 -> CodecUtils.decodeEtc1(texture2D.getData(), texture2D.getWidth(), texture2D.getHeight());
            default -> throw new UnsupportedOperationException("texture format: " + texture2D.getTextureFormat());
        };
        ImageIO.write(image, "png", Files.newOutputStream(path.resolve(texture2D.getName() + ".png")));
    }
}
