package top.frankyang.unityfs4j.impl;

import top.frankyang.unityfs4j.engine.UnityClass;
import top.frankyang.unityfs4j.engine.UnityField;
import top.frankyang.unityfs4j.engine.UnityObject;

@UnityClass("pair")
public interface Pair<T, U> extends UnityObject {
    @UnityField("first")
    T getFirst();

    @UnityField("second")
    U getSecond();
}
