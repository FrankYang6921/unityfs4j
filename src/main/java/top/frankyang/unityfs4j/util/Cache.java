package top.frankyang.unityfs4j.util;

import lombok.val;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

public class Cache<K, V> {
    protected final Map<K, SoftReference<V>> map = new HashMap<>();

    public <X extends Throwable> V computeIfAbsent(K key, ThrowingSupplier<? extends V, X> supplier) throws X {
        return computeIfAbsent(key, k -> supplier.get());
    }

    public <X extends Throwable> V computeIfAbsent(K key, ThrowingFunction<? super K, ? extends V, X> function) throws X {
        val ref = map.get(key);
        V v;
        if (ref != null && (v = ref.get()) != null) {
            return v;
        }
        v = function.apply(key);
        map.put(key, new SoftReference<>(v));
        return v;
    }

    public interface ThrowingSupplier<T, X extends Throwable> {
        T get() throws X;
    }

    public interface ThrowingFunction<T, U, X extends Throwable> {
        U apply(T t) throws X;
    }
}
