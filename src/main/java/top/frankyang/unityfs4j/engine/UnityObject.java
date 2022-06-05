package top.frankyang.unityfs4j.engine;

import top.frankyang.unityfs4j.asset.ObjectInfo;
import top.frankyang.unityfs4j.asset.UnityType;

import java.util.Map;

public interface UnityObject {
    ObjectInfo getObjectInfo();

    UnityType getUnityType();

    Map<String, Object> getFields();

    <T> T getField(String key);

    <T> T setField(String key, T value);
}
