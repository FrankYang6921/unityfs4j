package top.frankyang.unityfs4j.impl;

import top.frankyang.unityfs4j.engine.UnityClass;
import top.frankyang.unityfs4j.engine.UnityField;
import top.frankyang.unityfs4j.engine.UnityObject;

@UnityClass
public interface TextAsset extends UnityObject {
    @UnityField("m_Script")
    Object getScript();

    default boolean isText() {
        return getScript() instanceof String;
    }

    default boolean isBinary() {
        return getScript() instanceof byte[];
    }

    default String getString() {
        return (String) getScript();
    }

    default byte[] getBytes() {
        return (byte[]) getScript();
    }
}
