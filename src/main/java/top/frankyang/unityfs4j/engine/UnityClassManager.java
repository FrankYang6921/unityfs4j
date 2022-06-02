package top.frankyang.unityfs4j.engine;

import lombok.val;
import top.frankyang.unityfs4j.asset.ObjectInfo;
import top.frankyang.unityfs4j.asset.TypeTree;
import top.frankyang.unityfs4j.impl.PPtr;
import top.frankyang.unityfs4j.impl.Pair;
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

    public UnityObject createObject(ObjectInfo objectInfo, TypeTree typeTree, Map<String, Object> fields) {
        val rawType = StringUtils.substrTo(typeTree.getType(), '<');
        if (registry.containsKey(rawType)) {
            return interceptors
                .computeIfAbsent(registry.get(rawType), Interceptor::new).create(objectInfo, typeTree, fields);
        }
        return new UnityObjectImpl(objectInfo, typeTree, fields);
    }

    private void registerDefaults() {
        register(PPtr.class);
        register(Pair.class);
    }

    private static class UnityClassManagerSingleton {
        static final UnityClassManager INSTANCE = new UnityClassManager();
    }
}
