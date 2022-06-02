package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;
import lombok.var;
import top.frankyang.unityfs4j.engine.UnityClassManager;
import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.exception.ObjectFormatException;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.BufferUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
public class ObjectInfo {
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

    protected UnityObject createObject(TypeTree typeTree, Map<String, Object> fields) {
        return UnityClassManager.getInstance().createObject(this, typeTree, fields);
    }

    public void load() {
        val formatVersion = asset.formatVersion;
        pathId = readId();
        offset = payload.readInt() + asset.contentOffset;
        size = payload.readInt();
        typeId = payload.readInt();
        if (formatVersion < 17) {
            classId = payload.readShort();
        } else {
            typeId = classId = asset.getTypeMetadata().getClassIds().get(typeId);
        }
        if (formatVersion <= 10) {
            destroyed = payload.readShort() != 0;
        }
        if (formatVersion >= 11 && formatVersion <= 16) {
            payload.readShort();  // DK
        }
        if (formatVersion >= 15 && formatVersion <= 16) {
            payload.readByte();  // DK
        }
    }

    public final TypeTree getTypeTree() {
        if (typeTree == null) {
            typeTree = findTypeTree();
        }
        return typeTree;
    }

    protected TypeTree findTypeTree() {
        if (typeId < 0) {
            val typeTrees = asset.getTypeMetadata().getTypes();
            if (typeTrees.containsKey(typeId)) {
                return typeTrees.get(typeId);
            }
            if (typeTrees.containsKey(classId)) {
                return typeTrees.get(classId);
            }
            return TypeMetadata.getInstance().getTypes().get(classId);
        }
        return asset.getTypes().get(typeId);
    }

    public final UnityObject getObject() {
        if (object == null) {
            object = readObject();
        }
        return (UnityObject) object;
    }

    protected Object readObject() {
        payload.seek(asset.getOffset() + offset);
        return read(getTypeTree(), payload);
    }

    protected Object read(TypeTree typeTree, RandomAccess buf) {
        Object result = null;
        var align = false;
        val expected = typeTree.getSize();
        val ptrBefore = buf.tell();

        var firstChild = typeTree.getChildren().size() > 0 ?
            typeTree.getChildren().get(0) : new TypeTree(asset.getFormatVersion());
        val type = typeTree.getType();
        switch (type) {
            case "bool":
                result = buf.readBoolean();
                break;
            case "SInt8":
                result = buf.readByte();
                break;
            case "UInt8":
            case "char":
                result = buf.readUnsignedByte();
                break;
            case "SInt16":
            case "short":
                result = buf.readShort();
                break;
            case "UInt16":
            case "unsigned short":
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
                val size = typeTree.getSize();
                align = firstChild.isAligned();
                result = new String(BufferUtils.read(buf, size < 0 ? buf.readInt() : size), UTF_8);
                break;
        }
        blk:
        if (result == null) {  // Not primitive
            if (typeTree.isArray()) {
                firstChild = typeTree;
            }
            if (firstChild != null &&
                firstChild.isArray()) {
                align = firstChild.isAligned();
                result = readArray(buf.readInt(), firstChild.getChildren().get(1), buf);
                break blk;
            }
            if (type.startsWith("Exposed")) {
                val info = new ExposedObjectInfo(asset);
                val raw = new LinkedHashMap<String, Object>();
                for (TypeTree child : typeTree.getChildren()) {
                    raw.put(child.getName(), info.read(child, buf));
                }
                result = createObject(typeTree, raw);
                break blk;
            }
            val raw = new LinkedHashMap<String, Object>();
            for (TypeTree child : typeTree.getChildren()) {
                raw.put(child.getName(), read(child, buf));
            }
            result = createObject(typeTree, raw);
        }

        val ptrAfter = buf.tell();
        val actualSize = ptrAfter - ptrBefore;
        if (expected > 0 && actualSize < expected) {
            throw new ObjectFormatException(expected + " byte(s) expected, got " + actualSize);
        }
        if (align || typeTree.isAligned()) {
            buf.align();
        }
        return result;
    }

    protected Object readArray(int size, TypeTree elemType, RandomAccess buf) {
        Object result;
        switch (elemType.getType()) {
            case "bool":
                result = BufferUtils.readBooleans(buf, size);
                break;
            case "char":
            case "SInt8":
            case "UInt8":
                result = BufferUtils.read(buf, size);
                break;
            case "SInt16":
            case "short":
                result = BufferUtils.readShorts(buf, size);
                break;
            case "UInt16":
            case "unsigned short":
                result = BufferUtils.readUnsignedShorts(buf, size);
                break;
            case "SInt32":
            case "int":
                result = BufferUtils.readInts(buf, size);
                break;
            case "UInt32":
            case "unsigned int":
                result = BufferUtils.readUnsignedInts(buf, size);
                break;
            case "SInt64":
            case "UInt64":
                result = BufferUtils.readLongs(buf, size);
                break;
            case "float":
                buf.align();
                result = BufferUtils.readFloats(buf, size);
                break;
            case "double":
                buf.align();
                result = BufferUtils.readDoubles(buf, size);
                break;
            default:
                result = IntStream.range(0, size).mapToObj(i -> read(elemType, buf)).toArray();
        }
        return result;
    }

    private long readId() {
        return asset.longObjectId ? payload.readLong() : asset.readId();
    }
}
