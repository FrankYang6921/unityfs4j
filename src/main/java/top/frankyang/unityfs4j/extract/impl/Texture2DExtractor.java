package top.frankyang.unityfs4j.extract.impl;

import lombok.val;
import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.extract.Extractor;
import top.frankyang.unityfs4j.impl.Texture2D;
import top.frankyang.unityfs4j.util.CodecUtils;

import javax.imageio.ImageIO;
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
        val img = CodecUtils.decodeEtc(texture2D.getData(), texture2D.getWidth(), texture2D.getHeight());
        ImageIO.write(img, "png", Files.newOutputStream(path.resolve(texture2D.getName() + ".png")));
    }
}
