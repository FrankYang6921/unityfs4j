package top.frankyang.unityfs4j.engine;

import lombok.val;
import top.frankyang.unityfs4j.asset.ObjectInfo;
import top.frankyang.unityfs4j.asset.UnityType;
import top.frankyang.unityfs4j.impl.*;
import top.frankyang.unityfs4j.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnityClassManager {
    private final Map<String, Class<?>> registry = new HashMap<>();

    private final Map<Class<?>, Interceptor> interceptors = new ConcurrentHashMap<>();

    private UnityClassManager() {
        registerDefaults();
    }

    public static UnityClassManager getInstance() {
        return UnityClassManagerSingleton.INSTANCE;
    }

    public void register(Class<?> klass) {
        val annotation = klass.getAnnotation(UnityClass.class);
        if (annotation == null || !klass.isInterface() || !UnityObject.class.isAssignableFrom(klass)) {
            throw new IllegalArgumentException(klass.toString());
        }
        val className = annotation.value().isEmpty() ? klass.getSimpleName() : annotation.value();
        registry.put(className, klass);
    }

    public void register(Class<?>... classes) {
        for (Class<?> klass : classes) {
            register(klass);
        }
    }

    public UnityObject createObject(ObjectInfo objectInfo, UnityType unityType, Map<String, Object> fields) {
        val rawType = StringUtils.substrTo(unityType.getType(), '<');
        if (registry.containsKey(rawType)) {
            return interceptors
                .computeIfAbsent(registry.get(rawType), Interceptor::new).create(objectInfo, unityType, fields);
        }
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

    private static class UnityClassManagerSingleton {
        static final UnityClassManager INSTANCE = new UnityClassManager();
    }
}
