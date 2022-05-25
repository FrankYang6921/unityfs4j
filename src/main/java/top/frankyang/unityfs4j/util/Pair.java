package top.frankyang.unityfs4j.util;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.AbstractList;

@Value
@EqualsAndHashCode(callSuper = false)
public class Pair<T, U> extends AbstractList<Object> {
    T first;

    U second;

    public static <T, U> Pair<T, U> of(T t, U u) {
        return new Pair<>(t, u);
    }

    @Override
    public Object get(int index) {
        switch (index) {
            case 0:
                return first;
            case 1:
                return second;
            default:
                throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
    }

    @Override
    public int size() {
        return 2;
    }
}