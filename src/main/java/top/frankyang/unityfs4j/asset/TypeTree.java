package top.frankyang.unityfs4j.asset;

import lombok.Cleanup;
import lombok.Getter;
import lombok.val;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.io.RandomAccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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

    protected String data;

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
        val bufferSize = payload.readInt();
        val nodeSize = format >= 19 ? 32 : 24;
        val nodeData = IOUtils.readFully(payload.asInputStream(), nodeCount * nodeSize);
        data = new String(IOUtils.readFully(payload.asInputStream(), bufferSize), StandardCharsets.US_ASCII);

        val parents = new LinkedList<TypeTree>();
        parents.add(this);
        TypeTree curr;

        @Cleanup val buf = RandomAccess.of(nodeData);
        buf.setBigEndian(payload.isBigEndian());
        for (int i = 0; i < nodeCount; i++) {
            val version = buf.readShort();
            val depth = buf.readUnsignedByte();

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
            curr.isArray = buf.readBoolean();
            curr.type = getString(buf.readInt(), bufferSize);
            curr.name = getString(buf.readInt(), bufferSize);
            curr.size = buf.readInt();
            curr.index = buf.readInt();
            curr.flag = buf.readInt();

            if (nodeSize > 24) {  // Waste the rest bytes
                buf.skipBytes(nodeSize - 24);
            }
        }
    }

    protected String getString(int ptr, int size) {
        String data;
        if (ptr < 0) {
            ptr &= 0x7fffffff;
            data = STRINGS_DAT;
        } else if (ptr < size) {
            data = this.data;
        } else {
            return null;
        }
        return data.substring(ptr).split("\0")[0];
    }

    public boolean isPostAlign() {
        return (flag & 0x4000) > 0;
    }
}
