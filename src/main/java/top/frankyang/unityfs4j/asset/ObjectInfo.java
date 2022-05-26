package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;
import lombok.var;
import org.apache.commons.io.IOUtils;
import top.frankyang.unityfs4j.engine.UnityClassManager;
import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.Pair;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
public class ObjectInfo implements ObjectHolder {
    private final Asset asset;

    private final RandomAccess payload;

    protected long pathId;

    protected int offset;

    protected int size;

    protected int typeId;

    protected int classId;

    protected boolean destroyed;

    protected TypeTree typeTree;

    protected Object object;

    protected ObjectInfo(Asset asset) {
        this.asset = asset;
        payload = asset.getPayload();
    }

    public static UnityObject createObject(TypeTree typeTree, Map<String, Object> fields) {
        return UnityClassManager.getInstance().createObject(typeTree, fields);
    }

    public void load() throws IOException {
        val format = asset.getFormat();
        pathId = readId();
        offset = payload.readInt() + asset.getContentOffset();
        size = payload.readInt();
        typeId = payload.readInt();
        if (format < 17) {
            classId = payload.readShort();
        } else {
            typeId = classId = asset.getTypeMetadata().getClassIds().get(typeId);
        }
        if (format <= 10) {
            destroyed = payload.readShort() != 0;
        }
        if (format >= 11 && format <= 16) payload.readShort();  // DK
        if (format >= 15 && format <= 16) payload.readByte();  // DK
    }

    public final TypeTree getTypeTree() {
        if (typeTree == null) {
            typeTree = findTypeTree();
        }
        return typeTree;
    }

    protected TypeTree findTypeTree() {
        if (typeId < 0) {
            val typeTrees = asset.getTypeMetadata().getTrees();
            if (typeTrees.containsKey(typeId)) {
                return typeTrees.get(typeId);
            }
            if (typeTrees.containsKey(classId)) {
                return typeTrees.get(classId);
            }
            return TypeMetadata.getInstance().getTrees().get(classId);
        }
        return asset.getTypes().get(typeId);
    }

    @Override
    public final Object getObject() {
        if (object == null) {
            try {
                object = readObject();
            } catch (IOException e) {
                object = null;
            }
        }
        return object;
    }

    protected Object readObject() throws IOException {
        payload.seek(asset.getOffset() + offset);
        return readValue(getTypeTree(), payload);
    }

    protected Object readValue(TypeTree typeTree, RandomAccess buf) throws IOException {
        Object result = null;
        var align = false;
        val expectedSize = typeTree.getSize();
        val ptrBefore = buf.tell();

        var firstChild = typeTree.getChildren().size() > 0 ?
            typeTree.getChildren().get(0) :
            new TypeTree(asset.getFormat());
        val type = typeTree.getType();
        switch (type) {
            case "bool":
                result = buf.readBoolean();
                break;
            case "SInt8":
                result = buf.readByte();
                break;
            case "UInt8":
                result = buf.readUnsignedByte();
                break;
            case "SInt16":
                result = buf.readShort();
                break;
            case "UInt16":
                result = buf.readUnsignedShort();
                break;
            case "SInt32":
            case "int":
                result = buf.readInt();
                break;
            case "UInt32":
            case "unsigned int":
                result = buf.readUnsignedInt();
                break;
            case "SInt64":
            case "UInt64":
                result = buf.readLong();
                break;
            case "float":
                buf.align();
                result = buf.readFloat();
                break;
            case "double":
                buf.align();
                result = buf.readDouble();
                break;
            case "string":
                val size = typeTree.getSize() < 0 ? buf.readInt() : typeTree.getSize();
                result = new String(IOUtils.readFully(buf.asInputStream(), size), UTF_8);
                align = firstChild.isPostAlign();
                break;
        }
        blk:
        if (result == null) {  // Not primitive
            if (typeTree.isArray()) {
                firstChild = typeTree;
            }
            if (type.startsWith("PPtr<")) {
                val ptr = new ObjectPtr(typeTree, asset);
                ptr.load(buf);
                result = ptr.isValid() ? ptr : null;
                break blk;
            }
            if (firstChild != null &&
                firstChild.isArray()) {
                align = firstChild.isPostAlign();
                val size = buf.readInt();
                val elemType = firstChild.getChildren().get(1);
                if (elemType.getType().equals("char") ||
                    elemType.getType().equals("UInt8")) {
                    result = IOUtils.readFully(buf.asInputStream(), size);
                    break blk;
                }
                val list = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    list.add(readValue(elemType, buf));
                }
                result = list;
                break blk;
            }
            if ("pair".equals(type)) {
                val children = typeTree.getChildren();
                if (children.size() != 2) {
                    throw new InvalidObjectException("2 children expected, got" + children.size());
                }
                val first = readValue(children.get(0), buf);
                val second = readValue(children.get(1), buf);
                result = Pair.of(first, second);
                break blk;
            }
            if (type.startsWith("Exposed")) {
                val info = new ExposedObjectInfo(asset);
                val raw = new LinkedHashMap<String, Object>();
                for (TypeTree child : typeTree.getChildren()) {
                    raw.put(child.getName(), info.readValue(child, buf));
                }
                result = createObject(typeTree, raw);
                break blk;
            }
            val raw = new LinkedHashMap<String, Object>();
            for (TypeTree child : typeTree.getChildren()) {
                raw.put(child.getName(), readValue(child, buf));
            }
            result = createObject(typeTree, raw);
        }

        val ptrAfter = buf.tell();
        val actualSize = ptrAfter - ptrBefore;
        if (expectedSize > 0 && actualSize < expectedSize) {
            throw new IOException(expectedSize + " byte(s) expected, got " + actualSize);
        }
        if (align || typeTree.isPostAlign()) {
            buf.align();
        }
        return result;
    }

    private long readId() throws IOException {
        return asset.isLongObjectId() ? payload.readLong() : asset.readId();
    }
}
