package top.frankyang.unityfs4j.engine;

import lombok.val;
import net.sf.cglib.proxy.*;
import top.frankyang.unityfs4j.asset.ObjectInfo;
import top.frankyang.unityfs4j.asset.TypeTree;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

class Interceptor implements MethodInterceptor, CallbackFilter {
    private final Enhancer enhancer = new Enhancer();

    private final Map<String, String> getters = new HashMap<>();

    private final Map<String, String> setters = new HashMap<>();

    Interceptor(Class<?> klass) {
        enhancer.setSuperclass(UnityObjectImpl.class);
        enhancer.setCallbacks(new Callback[]{NoOp.INSTANCE, this});
        enhancer.setCallbackFilter(this);
        enhancer.setInterfaces(new Class[]{klass});
        for (Method method : klass.getMethods()) {
            val annotation = method.getAnnotation(UnityField.class);
            if (annotation == null) continue;
            if (method.getReturnType() == void.class) {
                setters.put(method.getName(), annotation.value());
            } else {
                getters.put(method.getName(), annotation.value());
            }
        }
    }

    public UnityObject create(ObjectInfo objectInfo, TypeTree typeTree, Map<String, Object> fields) {
        return (UnityObject) enhancer.create(
            new Class[]{ObjectInfo.class, TypeTree.class, Map.class,}, new Object[]{objectInfo, typeTree, fields});
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        val name = method.getName();
        val unityObj = (UnityObject) obj;
        if (getters.containsKey(name)) {
            return unityObj.getField(getters.get(name));
        }
        if (setters.containsKey(name)) {
            unityObj.setField(setters.get(name), args[0]);
            return null;
        }
        return proxy.invokeSuper(obj, args);
    }

    @Override
    public int accept(Method method) {
        if (getters.containsKey(method.getName()) ||
            setters.containsKey(method.getName())) {
            return 1;
        }
        return 0;
    }
}
