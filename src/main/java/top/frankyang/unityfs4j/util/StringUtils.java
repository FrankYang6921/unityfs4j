package top.frankyang.unityfs4j.util;

import lombok.experimental.UtilityClass;
import lombok.val;

import java.nio.file.Path;

@UtilityClass
public class StringUtils {
    public String getFileName(Path path) {
        return substrTo(path.getFileName().toString(), '.');
    }

    public String substrTo(String string, char c) {
        val index = string.indexOf(c);
        return index > 0 ? string.substring(0, index) : string;
    }

    public String substrFrom(String string, char c) {
        val index = string.indexOf(c);
        return index > 0 ? string.substring(index + 1) : string;
    }
}
