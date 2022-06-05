package top.frankyang.unityfs4j.engine;

import lombok.val;
import net.sf.cglib.proxy.*;
import top.frankyang.unityfs4j.asset.ObjectInfo;
import top.frankyang.unityfs4j.asset.UnityType;
import top.frankyang.unityfs4j.exception.ObjectMappingException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

class Interceptor implements MethodInterceptor, CallbackFilter {
    private static final Class<?>[] CONSTRUCTOR_TYPES = new Class[]{ObjectInfo.class, UnityType.class, Map.class,};

    private final Enhancer enhancer = new Enhancer();

    private final Map<Method, Signature> signatures = new ConcurrentHashMap<>();

    private final Map<Signature, String> getters = new HashMap<>();

    private final Map<Signature, String> setters = new HashMap<>();

    Interceptor(Class<?> klass) {
        enhancer.setSuperclass(UnityObjectImpl.class);
        enhancer.setCallbacks(new Callback[]{NoOp.INSTANCE, this});
        enhancer.setCallbackFilter(this);
        enhancer.setInterfaces(new Class[]{klass});
        for (Method method : klass.getMethods()) {
            val annotation = method.getAnnotation(UnityField.class);
            if (annotation == null) continue;
            if (method.getReturnType() == void.class && method.getParameters().length == 1) {
                setters.put(signatureOf(method), annotation.value());
                continue;
            }
            if (method.getReturnType() != void.class && method.getParameters().length == 0) {
                getters.put(signatureOf(method), annotation.value());
                continue;
            }
            throw new ObjectMappingException(method + " is not recognized as a getter or setter");
        }
    }

    UnityObject create(ObjectInfo objectInfo, UnityType unityType, Map<String, Object> fields) {
        return (UnityObject) enhancer.create(CONSTRUCTOR_TYPES, new Object[]{objectInfo, unityType, fields});
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        val signature = signatureOf(method);
        val unityObj = (UnityObject) obj;
        if (getters.containsKey(signature)) {
            return unityObj.getField(getters.get(signature));
        }
        if (setters.containsKey(signature)) {
            unityObj.setField(setters.get(signature), args[0]);
            return null;
        }
        return proxy.invokeSuper(obj, args);
    }

    @Override
    public int accept(Method method) {
        if (getters.containsKey(signatureOf(method)) ||
            setters.containsKey(signatureOf(method))) {
            return 1;
        }
        return 0;
    }

    private Signature signatureOf(Method method) {
        return signatures.computeIfAbsent(method, Signature::new);
    }

    private static class Signature {
        final String name;

        final Class<?>[] paramTypes;

        final Class<?> returnType;

        Signature(Method method) {
            name = method.getName();
            paramTypes = method.getParameterTypes();
            returnType = method.getReturnType();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Signature signature = (Signature) o;
            return Objects.equals(name, signature.name) &&
                Arrays.equals(paramTypes, signature.paramTypes) &&
                Objects.equals(returnType, signature.returnType);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name, returnType);
            result = 31 * result + Arrays.hashCode(paramTypes);
            return result;
        }
    }
}
