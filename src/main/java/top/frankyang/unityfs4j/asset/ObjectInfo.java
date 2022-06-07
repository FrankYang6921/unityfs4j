package top.frankyang.unityfs4j.asset;

import lombok.Getter;
import lombok.val;
import lombok.var;
import top.frankyang.unityfs4j.engine.UnityClassManager;
import top.frankyang.unityfs4j.engine.UnityObject;
import top.frankyang.unityfs4j.exception.ObjectFormatException;
import top.frankyang.unityfs4j.impl.StreamData;
import top.frankyang.unityfs4j.io.RandomAccess;
import top.frankyang.unityfs4j.util.BufferUtils;
import top.frankyang.unityfs4j.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
public class ObjectInfo {
    private final Asset asset;

    private final RandomAccess payload;

    protected long pathId;

    protected int offset;

    protected int length;

    protected int typeId;

    protected int classId;

    protected boolean destroyed;

    protected UnityType unityType;  // Cached

    protected Object object;  // Cached

    protected ObjectInfo(Asset asset) {
        this.asset = asset;
        payload = asset.getPayload();
        load();
    }

    protected UnityObject createObject(UnityType unityType, Map<String, Object> fields) {
        return UnityClassManager.getInstance().createObject(this, unityType, fields);
    }

    protected void load() {
        val formatVersion = asset.getFormatVersion();
        pathId = readId();
        offset = payload.readInt() + asset.getContentOffset();
        length = payload.readInt();
        typeId = payload.readInt();
        if (formatVersion < 17) {
            classId = payload.readShort();
        } else {
            typeId = classId = asset.getUnityTypes().getClassIds().get(typeId);
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

    public final UnityType getUnityType() {
        if (unityType == null) {
            unityType = findUnityType();
        }
        return unityType;
    }

    protected UnityType findUnityType() {
        if (typeId < 0) {
            val typeTrees = asset.getUnityTypes().getTypes();
            if (typeTrees.containsKey(typeId)) {
                return typeTrees.get(typeId);
            }
            if (typeTrees.containsKey(classId)) {
                return typeTrees.get(classId);
            }
            return UnityTypes.getInstance().getTypes().get(classId);
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
        return read(getUnityType(), payload);
    }

    protected Object read(UnityType unityType, RandomAccess buf) {
        Object result = null;
        var align = false;
        val expected = unityType.getSize();
        val ptrBefore = buf.tell();

        var firstChild = unityType.getChildren().size() > 0 ? unityType.getChildren().get(0) : UnityType.DUMMY;
        val type = unityType.getType();
        // Read primitive
        switch (type) {
            case "bool":  // Boolean
                result = buf.readBoolean();
                break;
            case "SInt8":  // Byte
                result = buf.readByte();
                break;
            case "UInt8":
            case "char":  // Integer
                result = buf.readUnsignedByte();
                break;
            case "SInt16":
            case "short":  // Short
                result = buf.readShort();
                break;
            case "UInt16":
            case "unsigned short":  // Integer
                result = buf.readUnsignedShort();
                break;
            case "SInt32":
            case "int":  // Integer
                result = buf.readInt();
                break;
            case "UInt32":
            case "unsigned int":  // Long
                result = buf.readUnsignedInt();
                break;
            case "SInt64":
            case "UInt64":  // Long
                result = buf.readLong();
                break;
            case "float":  // Float
                buf.align();
                result = buf.readFloat();
                break;
            case "double":  // Double
                buf.align();
                result = buf.readDouble();
                break;
            case "string":  // String
                val size = unityType.getSize();
                align = firstChild.isAligned();
                result = StringUtils.bytesOrString(BufferUtils.read(buf, size < 0 ? buf.readInt() : size), UTF_8);
                break;
        }
        if (result == null) {  // Not primitive
            if (unityType.isArray()) {
                firstChild = unityType;
            }
            if (firstChild != null &&
                firstChild.isArray()) {  // Read array
                align = firstChild.isAligned();
                result = readArray(buf.readInt(), firstChild.getChildren().get(1), buf);
            } else {  // Read normal object
                val exposed = type.startsWith("Exposed");
                val raw = new LinkedHashMap<String, Object>();
                for (UnityType child : unityType.getChildren()) {
                    raw.put(child.getName(), exposed ?
                        readExposed(child, buf) : read(child, buf)
                    );
                }
                result = createObject(unityType, raw);
                if (result instanceof StreamData) {
                    val streamedData = (StreamData) result;
                    streamedData.setAsset(getAsset().resolveAsset(streamedData.getPath()));
                }
            }
        }

        val ptrAfter = buf.tell();
        val actualSize = ptrAfter - ptrBefore;
        if (expected > 0 && actualSize < expected) {
            throw new ObjectFormatException(expected + " byte(s) expected, got " + actualSize);
        }
        if (align || unityType.isAligned()) {
            buf.align();
        }
        return result;
    }

    protected Object readExposed(UnityType unityType, RandomAccess buf) {
        if ("exposedName".equals(unityType.getName())) {
            buf.readInt();
            return "";
        }
        return read(unityType, buf);
    }

    protected Object readArray(int size, UnityType elemType, RandomAccess buf) {
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
                Object[] array = new Object[size];
                for (int i = 0; i < size; i++) {
                    array[i] = read(elemType, buf);
                }
                result = array;
        }
        return result;
    }

    private long readId() {
        return asset.isLongObjectId() ? payload.readLong() : asset.readId();
    }
}
