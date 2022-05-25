package top.frankyang.unityfs4j.engine;

import lombok.Getter;
import lombok.val;
import top.frankyang.unityfs4j.asset.TypeTree;

import java.util.Collections;
import java.util.Map;

public class UnityObjectImpl implements UnityObject {
    @Getter
    private final TypeTree typeTree;

    private final Map<String, Object> fields;

    protected UnityObjectImpl(TypeTree typeTree, Map<String, Object> fields) {
        this.typeTree = typeTree;
        this.fields = fields;
    }

    @Override
    public Map<String, Object> getFields() {
        return Collections.unmodifiableMap(fields);
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
        val string = fields.toString();
        return typeTree.getType() + '(' + string.substring(1, string.length() - 1) + ')';
    }
}
