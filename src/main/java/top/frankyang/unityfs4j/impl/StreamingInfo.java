package top.frankyang.unityfs4j.impl;

import top.frankyang.unityfs4j.engine.UnityClass;
import top.frankyang.unityfs4j.engine.UnityField;

@UnityClass
public interface StreamingInfo extends StreamData {
    @Override
    @UnityField("offset")
    long getOffset();

    @Override
    @UnityField("size")
    long getSize();

    @Override
    @UnityField("path")
    String getPath();
}
