package top.frankyang.unityfs4j.util;

import lombok.experimental.UtilityClass;


import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

@UtilityClass
public class StringUtils {
    public String substrTo(String string, char c) {
        var index = string.indexOf(c);
        return index > 0 ? string.substring(0, index) : string;
    }

    public String substrFrom(String string, char c) {
        var index = string.indexOf(c);
        return index > 0 ? string.substring(index + 1) : string;
    }

    public Object bytesOrString(byte[] array, Charset charset) {
        var decoder = charset.newDecoder()  // Explicitly ask for exception
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        var buf = ByteBuffer.wrap(array);
        try {
            return decoder.decode(buf).toString();
        } catch (CharacterCodingException e) {
            return array;
        }
    }

    public byte[] bytesOrString(Object object, Charset charset) {
        if (object instanceof byte[]) return (byte[]) object;
        if (object instanceof String s) {
            return s.getBytes(charset);
        }
        throw new IllegalArgumentException(object.toString());
    }
}
