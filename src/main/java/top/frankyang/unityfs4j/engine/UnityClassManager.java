package top.frankyang.unityfs4j.engine;


import top.frankyang.unityfs4j.asset.ObjectInfo;
import top.frankyang.unityfs4j.asset.UnityType;
import top.frankyang.unityfs4j.impl.*;
import top.frankyang.unityfs4j.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnityClassManager {
    private final Map<String, Class<?>> registry = new HashMap<>();

    private final Map<Class<?>, UnityClassProxy> proxyMap = new ConcurrentHashMap<>();

    private UnityClassManager() {
        registerDefaults();
    }

    public static UnityClassManager getInstance() {
        return Holder.INSTANCE;
    }

    public void register(Class<?> klass) {
        var annotation = klass.getAnnotation(UnityClass.class);
        if (annotation == null || !klass.isInterface() || !UnityObject.class.isAssignableFrom(klass)) {
            throw new IllegalArgumentException(klass.toString());
        }
        var className = annotation.value().isEmpty() ? klass.getSimpleName() : annotation.value();
        registry.put(className, klass);
    }

    public void register(Class<?>... classes) {
        for (Class<?> klass : classes) {
            register(klass);
        }
    }

    public UnityObject createObject(ObjectInfo objectInfo, UnityType unityType, Map<String, Object> fields) {
        var rawType = StringUtils.substrTo(unityType.getType(), '<');  // let's ignore generics
        if (registry.containsKey(rawType)) {
            return proxyMap
                .computeIfAbsent(registry.get(rawType), UnityClassProxy::new)
                .createObject(objectInfo, unityType, fields);
        }
        // no class for it, just return a plain object
        return new UnityObjectImpl(objectInfo, unityType, fields);
    }

    private void registerDefaults() {
        register(
            PPtr.class,
            Pair.class,
            StreamedResource.class,
            StreamingInfo.class,
            TextAsset.class,
            Texture2D.class,
            Vector.class
        );
    }

    private static class Holder {
        static final UnityClassManager INSTANCE = new UnityClassManager();
    }
}
