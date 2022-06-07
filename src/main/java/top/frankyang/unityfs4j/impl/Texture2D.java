package top.frankyang.unityfs4j.impl;

import lombok.var;
import top.frankyang.unityfs4j.engine.UnityClass;
import top.frankyang.unityfs4j.engine.UnityField;
import top.frankyang.unityfs4j.engine.UnityObject;

@UnityClass
public interface Texture2D extends UnityObject {
    @UnityField("m_Width")
    int getWidth();

    @UnityField("m_Height")
    int getHeight();

    @UnityField("m_TextureFormat")
    int getTextureFormat();

    @UnityField("image data")
    byte[] getImageData();

    @UnityField("m_StreamData")
    StreamData getStreamData();

    default byte[] getData() {
        var data = getImageData();
        if (data.length == 0) {
            data = getStreamData().getData();
        }
        return data;
    }
}
