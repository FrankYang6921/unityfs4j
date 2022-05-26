package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.io.Whence;
import top.frankyang.unityfs4j.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
public class TypeTree {
    public static final String STRINGS_DAT;

    public static final String NULL = "(null)";

    static {
        try {
            STRINGS_DAT = IOUtils.resourceToString("/strings.dat", StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private final int format;

    private final List<TypeTree> children = new ArrayList<>();

    protected String string;

    protected short version;

    protected boolean isArray;

    protected String type = NULL;

    protected String name = NULL;

    protected int size;

    protected int index;

    protected int flag;

    protected TypeTree(int format) {
        this.format = format;
    }

    protected void load(RandomAccess payload) throws IOException {
        if (format >= 12 || format == 10) {
            loadBlob(payload);
            return;
        }
        throw new UnsupportedOperationException();  // TODO
    }

    protected void loadBlob(RandomAccess payload) throws IOException {
        val nodeCount = payload.readInt();
        val stringSize = payload.readInt();
        val nodeSize = format >= 19 ? 32 : 24;
        val dataSize = nodeCount * nodeSize;
        val bodySize = dataSize + stringSize;
        val oldPointer = payload.tell();
        payload.seek(dataSize, Whence.POINTER);
        string = new String(IOUtils.readFully(payload.asInputStream(), stringSize), UTF_8);
        payload.seek(-bodySize, Whence.POINTER);

        val parents = new LinkedList<TypeTree>();
        parents.add(this);
        TypeTree curr;

        for (int i = 0; i < nodeCount; i++) {
            val version = payload.readShort();
            val depth = payload.readUnsignedByte();

            if (depth == 0) {
                curr = this;
            } else {
                while (parents.size() > depth) {
                    parents.removeLast();
                }
                curr = new TypeTree(format);
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
        return StringUtils.truncateTo(data.substring(ptr), '\0');
    }

    public boolean isPostAlign() {
        return (flag & 0x4000) > 0;
    }
}
