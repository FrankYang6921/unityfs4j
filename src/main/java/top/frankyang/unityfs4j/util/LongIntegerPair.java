package top.frankyang.unityfs4j.util;

import lombok.Value;

@Value
public class LongIntegerPair {
    long first;

    int second;

    public static LongIntegerPair of(long first, int second) {
        return new LongIntegerPair(first, second);
    }
}