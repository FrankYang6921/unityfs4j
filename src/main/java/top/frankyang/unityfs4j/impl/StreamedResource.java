package top.frankyang.unityfs4j.impl;

import top.frankyang.unityfs4j.engine.UnityClass;
import top.frankyang.unityfs4j.engine.UnityField;

@UnityClass
public interface StreamedResource extends StreamData {
    @Override
    @UnityField("m_Offset")
    long getOffset();

    @Override
    @UnityField("m_Size")
    long getSize();

    @Override
    @UnityField("m_Source")
    String getPath();
}
