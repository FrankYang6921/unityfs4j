package top.frankyang.unityfs4j.engine;

import lombok.Getter;

import top.frankyang.unityfs4j.asset.ObjectInfo;
import top.frankyang.unityfs4j.asset.UnityType;

import java.util.Map;

@Getter
public class UnityObjectImpl implements UnityObject {
    private final ObjectInfo objectInfo;

    private final UnityType unityType;

    private final Map<String, Object> fields;

    protected UnityObjectImpl(ObjectInfo objectInfo, UnityType unityType, Map<String, Object> fields) {
        this.unityType = unityType;
        this.fields = fields;
        this.objectInfo = objectInfo;
    }

    @Override
    public <T> T getField(String key) {
        //noinspection unchecked
        return (T) fields.get(key);
    }

    @Override
    public <T> T setField(String key, T value) {
        //noinspection unchecked
        return (T) fields.put(key, value);
    }

    @Override
    public String toString() {
        var string = fields.toString();
        return unityType.getType() + '(' + string.substring(1, string.length() - 1) + ')';
    }
}
