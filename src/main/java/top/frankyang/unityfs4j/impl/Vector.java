package top.frankyang.unityfs4j.impl;

import top.frankyang.unityfs4j.engine.UnityClass;
import top.frankyang.unityfs4j.engine.UnityField;
import top.frankyang.unityfs4j.engine.UnityObject;

import java.util.Arrays;
import java.util.List;

@UnityClass("vector")
public interface Vector<E> extends UnityObject, List<E> {
    @UnityField("Array")
    Object[] getArray();

    default List<E> asList() {
        //noinspection unchecked
        return (List<E>) getFields().computeIfAbsent("_list", s -> Arrays.asList(getArray()));
    }
}
