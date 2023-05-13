package top.frankyang.unityfs4j.engine;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import lombok.SneakyThrows;
import top.frankyang.unityfs4j.asset.ObjectInfo;
import top.frankyang.unityfs4j.asset.UnityType;
import top.frankyang.unityfs4j.exception.ObjectMappingException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

public class UnityClassProxy {
    private final ClassPool pool = new ClassPool(ClassPool.getDefault());

    private final MethodHandle handle;

    @SneakyThrows
    public UnityClassProxy(Class<?> klass) {
        klass = makeProxyClass(klass);
        var ctor = klass.getDeclaredConstructor(ObjectInfo.class, UnityType.class, Map.class);
        ctor.setAccessible(true);
        handle = MethodHandles.lookup().unreflectConstructor(ctor);
    }

    private CtClass toCtClass(Class<?> klass) throws NotFoundException {
        return pool.get(klass.getName());
    }

    private CtClass[] toCtClass(Class<?>... classes) throws NotFoundException {
        var result = new CtClass[classes.length];
        for (int i = 0; i < classes.length; i++) {
            result[i] = toCtClass(classes[i]);
        }
        return result;
    }

    private Class<?> makeProxyClass(Class<?> klass) throws Exception {
        var name = klass.getName() + "$$ImplByUnityFS4J$$" +
            Long.toString(System.nanoTime(), 36);
        var proxy = pool.makeClass(name, toCtClass(UnityObjectImpl.class));
        proxy.addInterface(toCtClass(klass));

        for (Method method : klass.getMethods()) {
            if (!Modifier.isAbstract(method.getModifiers())) continue;
            var annotation = method.getDeclaredAnnotation(UnityField.class);
            if (annotation == null) continue;
            if (method.getReturnType() != void.class &&
                method.getParameters().length == 0) {
                proxy.addMethod(makeGetter(method, proxy, annotation.value()));
                continue;
            }
            if (method.getReturnType() == void.class &&
                method.getParameters().length == 1) {
                proxy.addMethod(makeSetter(method, proxy, annotation.value()));
                continue;
            }
            throw new ObjectMappingException(method + " is not recognized as a getter or setter");
        }

        return proxy.toClass();
    }

    private CtMethod makeGetter(Method method, CtClass declaring, String key) throws Exception {
        var result = new CtMethod(toCtClass(method.getReturnType()), method.getName(), new CtClass[0], declaring);
        result.setModifiers(Modifier.PUBLIC);
        result.setBody("{return ($r) $0.getField(\"%s\");}".formatted(key));
        return result;
    }

    private CtMethod makeSetter(Method method, CtClass declaring, String key) throws Exception {
        var result = new CtMethod(CtClass.voidType, method.getName(), toCtClass(method.getParameterTypes()), declaring);
        result.setModifiers(Modifier.PUBLIC);
        result.setBody("{$0.setField(\"%s\", $1);}".formatted(key));
        return result;
    }

    @SneakyThrows
    UnityObject createObject(ObjectInfo objectInfo, UnityType unityType, Map<String, Object> fields) {
        return (UnityObject) handle.invoke(objectInfo, unityType, fields);
    }
}
