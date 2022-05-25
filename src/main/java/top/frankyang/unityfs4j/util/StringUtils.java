package top.frankyang.unityfs4j.util;

import lombok.experimental.UtilityClass;
import lombok.val;

import java.nio.file.Path;

@UtilityClass
public class StringUtils {
    public String getName(Path path) {
        val name = path.getFileName().toString();
        val index = name.indexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }

    public String repeat(String string, int count) {
        if (count == 0) return "";
        val sb = new StringBuilder(string.length() * count);
        for (int i = 0; i < count; i++) sb.append(string);
        return sb.toString();
    }
}
