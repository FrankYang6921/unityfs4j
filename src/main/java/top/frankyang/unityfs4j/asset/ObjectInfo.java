package top.frankyang.unityfs4j.asset;

import lombok.Getter;
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
        var formatVersion = asset.getFormatVersion();
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
            var typeTrees = asset.getUnityTypes().getTypes();
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
        Object result;
        var align = false;
        var expected = unityType.getSize();
        var ptrBefore = buf.tell();

        var firstChild =
            unityType.getChildren().size() > 0 ?
                unityType.getChildren().get(0) :
                UnityType.DUMMY;
        var type = unityType.getType();
        // Read primitive
        result = switch (type) {
            case "bool" -> buf.readBoolean();
            case "SInt8" -> buf.readByte();
            case "UInt8", "char" -> buf.readUnsignedByte();
            case "SInt16", "short" -> buf.readShort();
            case "UInt16", "unsigned short" -> buf.readUnsignedShort();
            case "SInt32", "int" -> buf.readInt();
            case "UInt32", "unsigned int" -> buf.readUnsignedInt();
            case "SInt64", "long" -> buf.readLong();
            case "UInt64", "unsigned long" -> buf.readUnsignedLong();
            case "float" -> buf.align().readFloat();
            case "double" -> buf.align().readDouble();
            case "string" -> {
                var size = unityType.getSize();
                align = firstChild.isAligned();
                yield StringUtils.bytesOrString(BufferUtils.read(buf, size < 0 ? buf.readInt() : size), UTF_8);
            }
            default -> null;
        };
        if (result == null) {  // Non-primitive
            if (unityType.isArray()) {
                firstChild = unityType;
            }
            if (firstChild != null &&
                firstChild.isArray()) {  // Read array
                align = firstChild.isAligned();
                result = readArray(buf.readInt(), firstChild.getChildren().get(1), buf);
            } else {  // Read normal object
                var exposed = type.startsWith("Exposed");
                var raw = new LinkedHashMap<String, Object>();
                for (UnityType child : unityType.getChildren()) {
                    raw.put(child.getName(), exposed ?
                        readExposed(child, buf) : read(child, buf)
                    );
                }
                result = createObject(unityType, raw);
                if (result instanceof StreamData sd) {
                    sd.setAsset(getAsset().resolveAsset(sd.getPath()));
                }
            }
        }

        var ptrAfter = buf.tell();
        var actualSize = ptrAfter - ptrBefore;
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
        return switch (elemType.getType()) {
            case "bool" -> BufferUtils.readBooleans(buf, size);
            case "char", "SInt8", "UInt8" -> BufferUtils.read(buf, size);
            case "SInt16", "short" -> BufferUtils.readShorts(buf, size);
            case "UInt16", "unsigned short" -> BufferUtils.readUnsignedShorts(buf, size);
            case "SInt32", "int" -> BufferUtils.readInts(buf, size);
            case "UInt32", "unsigned int" -> BufferUtils.readUnsignedInts(buf, size);
            case "SInt64", "long" -> BufferUtils.readLongs(buf, size);
            case "UInt64", "unsigned long" -> BufferUtils.readUnsignedLongs(buf, size);
            case "float" -> BufferUtils.readFloats(buf.align(), size);
            case "double" -> BufferUtils.readDoubles(buf.align(), size);
            default -> {
                Object[] array = new Object[size];
                for (int i = 0; i < size; i++) {
                    array[i] = read(elemType, buf);
                }
                yield array;
            }
        };
    }

    private long readId() {
        return asset.isLongObjectId() ? payload.readLong() : asset.readId();
    }
}
