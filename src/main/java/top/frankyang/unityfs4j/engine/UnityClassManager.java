package top.frankyang.unityfs4j.engine;

import lombok.val;
import top.frankyang.unityfs4j.asset.TypeTree;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnityClassManager {
    private final Map<String, Class<?>> registry = new HashMap<>();

    private final Map<Class<?>, Interceptor> interceptors = new ConcurrentHashMap<>();

    private UnityClassManager() {
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

    public UnityObject createObject(TypeTree typeTree, Map<String, Object> fields) {
        if (!registry.containsKey(typeTree.getType())) {
            return new UnityObjectImpl(typeTree, fields);
        }
        return interceptors.computeIfAbsent(registry.get(typeTree.getType()), Interceptor::new)
            .create(typeTree, fields);
    }

    private static class UnityClassManagerSingleton {
        static final UnityClassManager INSTANCE = new UnityClassManager();
    }
}
