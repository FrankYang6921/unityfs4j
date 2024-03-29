package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.exception.ObjectFormatException;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.io.Whence;
import top.frankyang.unityfs4j.util.BufferUtils;
import top.frankyang.unityfs4j.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
public class UnityType {
    public static final UnityType DUMMY = new UnityType(15);

    private static final String STRINGS_DAT;

    static {
        try {
            STRINGS_DAT = IOUtils.resourceToString("/strings.dat", US_ASCII);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    protected final List<UnityType> children = new ArrayList<>();

    private final int format;

    protected String string;

    protected short version;

    protected boolean isArray;

    protected String type;

    protected String name;

    protected int size;

    protected int index;

    protected int flag;

    protected UnityType(int format) {
        this.format = format;
    }

    public List<UnityType> getChildren() {
        return Collections.unmodifiableList(children);
    }

    protected void load(RandomAccess payload) {
        if (format >= 12 || format == 10) {
            loadBlob(payload);
            return;
        }
        throw new UnsupportedOperationException();  // TODO implement it
    }

    protected void loadBlob(RandomAccess payload) {
        var nodeCount = payload.readInt();
        var stringSize = payload.readInt();
        var nodeSize = format >= 19 ? 32 : 24;
        var dataSize = nodeCount * nodeSize;
        var bodySize = dataSize + stringSize;
        var oldPointer = payload.tell();
        payload.seek(dataSize, Whence.POINTER);
        string = new String(BufferUtils.read(payload, stringSize), UTF_8);
        payload.seek(-bodySize, Whence.POINTER);

        var parents = new LinkedList<UnityType>();
        parents.add(this);
        UnityType curr;

        for (int i = 0; i < nodeCount; i++) {
            var version = payload.readShort();
            var depth = payload.readUnsignedByte();

            if (depth == 0) {
                curr = this;
            } else {
                while (parents.size() > depth) {
                    parents.removeLast();
                }
                curr = new UnityType(format);
                parents.getLast().children.add(curr);
                parents.addLast(curr);
            }

            curr.version = version;
            curr.isArray = payload.readBoolean();
            curr.type = getString(payload.readInt(), stringSize);
            curr.name = getString(payload.readInt(), stringSize);
            curr.size = payload.readInt();
            curr.index = payload.readInt();
            curr.flag = payload.readInt();
            if (type.length() == 0) {
                throw new ObjectFormatException();
            }

            if (nodeSize > 24) {  // Waste the rest bytes
                payload.skipBytes(nodeSize - 24);
            }
        }

        payload.seek(oldPointer + bodySize);
    }

    protected String getString(int ptr, int size) {
        String data;
        if (ptr < 0) {
            ptr &= 0x7fffffff;
            data = STRINGS_DAT;
        } else if (ptr < size) {
            data = this.string;
        } else {
            return null;
        }
        return StringUtils.substrTo(data.substring(ptr), '\0');
    }

    public boolean isAligned() {
        return (flag & 0x4000) > 0;
    }

    @Override
    public String toString() {
        return "UnityType{" + type + '}';
    }
}
