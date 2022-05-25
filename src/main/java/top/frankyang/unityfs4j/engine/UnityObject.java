package top.frankyang.unityfs4j.engine;

import top.frankyang.unityfs4j.asset.TypeTree;

import java.util.Map;

public interface UnityObject {
    TypeTree getTypeTree();

    Map<String, Object> getFields();

    <T> T getField(String key);

    <T> T setField(String key, T value);
}
