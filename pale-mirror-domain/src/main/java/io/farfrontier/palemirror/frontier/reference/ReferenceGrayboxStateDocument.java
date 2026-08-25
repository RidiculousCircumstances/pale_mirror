package io.farfrontier.palemirror.frontier.reference;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned, bounded binary carrier for a graybox canonical-state document.
 *
 * <p>This is deliberately a value codec, not Java serialization: the wire
 * format contains only the source-shaped map/list/scalar graph. Hydration
 * remains a separate all-or-nothing domain operation.</p>
 */
public final class ReferenceGrayboxStateDocument {
    private static final int MAGIC = 0x504D4753; // PMGS
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_COLLECTION_ENTRIES = 100_000;
    private static final byte NULL = 0, FALSE = 1, TRUE = 2, INTEGER = 3, LONG = 4, DOUBLE = 5, STRING = 6, LIST = 7, MAP = 8;

    private ReferenceGrayboxStateDocument() { }

    public static byte[] encode(Map<String, Object> state) {
        Map<String, Object> required = validateRoot(state);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            writeValue(output, required, 0);
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > MAX_BYTES) throw new IllegalArgumentException("graybox state document exceeds maximum size");
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("could not encode graybox state document", exception);
        }
    }

    public static Map<String, Object> decode(byte[] document) {
        byte[] required = Objects.requireNonNull(document, "document");
        if (required.length == 0 || required.length > MAX_BYTES) {
            throw new IllegalArgumentException("graybox state document has invalid size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(required))) {
            if (input.readInt() != MAGIC) throw new IllegalArgumentException("graybox state document magic is invalid");
            if (input.readInt() != VERSION) throw new IllegalArgumentException("graybox state document version is unsupported");
            Object decoded = readValue(input, 0);
            if (input.read() != -1) throw new IllegalArgumentException("graybox state document has trailing bytes");
            if (!(decoded instanceof Map<?, ?> map)) throw new IllegalArgumentException("graybox state document root must be a map");
            LinkedHashMap<String, Object> root = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("graybox state document root key is invalid");
                root.put(key, entry.getValue());
            }
            return validateRoot(root);
        } catch (IOException exception) {
            throw new IllegalArgumentException("graybox state document is truncated", exception);
        }
    }

    private static Map<String, Object> validateRoot(Map<String, Object> state) {
        return ReferenceGrayboxStateReader.envelope(Objects.requireNonNull(state, "state"));
    }

    private static void writeValue(DataOutputStream output, Object value, int depth) throws IOException {
        requireDepth(depth);
        if (value == null) {
            output.writeByte(NULL);
        } else if (value instanceof Boolean flag) {
            output.writeByte(flag ? TRUE : FALSE);
        } else if (value instanceof Integer integer) {
            output.writeByte(INTEGER); output.writeInt(integer);
        } else if (value instanceof Long number) {
            output.writeByte(LONG); output.writeLong(number);
        } else if (value instanceof Double number) {
            if (!Double.isFinite(number)) throw new IllegalArgumentException("graybox state document rejects non-finite float");
            output.writeByte(DOUBLE); output.writeLong(Double.doubleToRawLongBits(number));
        } else if (value instanceof String string) {
            output.writeByte(STRING); writeString(output, string);
        } else if (value instanceof List<?> list) {
            if (list.size() > MAX_COLLECTION_ENTRIES) throw new IllegalArgumentException("graybox state document list is too large");
            output.writeByte(LIST); output.writeInt(list.size());
            for (Object item : list) writeValue(output, item, depth + 1);
        } else if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_COLLECTION_ENTRIES) throw new IllegalArgumentException("graybox state document map is too large");
            output.writeByte(MAP); output.writeInt(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("graybox state document map key is invalid");
                writeString(output, key);
                writeValue(output, entry.getValue(), depth + 1);
            }
        } else {
            throw new IllegalArgumentException("graybox state document has unsupported value " + value.getClass().getName());
        }
    }

    private static Object readValue(DataInputStream input, int depth) throws IOException {
        requireDepth(depth);
        return switch (input.readUnsignedByte()) {
            case NULL -> null;
            case FALSE -> false;
            case TRUE -> true;
            case INTEGER -> input.readInt();
            case LONG -> input.readLong();
            case DOUBLE -> finiteDouble(Double.longBitsToDouble(input.readLong()));
            case STRING -> readString(input);
            case LIST -> readList(input, depth + 1);
            case MAP -> readMap(input, depth + 1);
            default -> throw new IllegalArgumentException("graybox state document has unknown value tag");
        };
    }

    private static List<Object> readList(DataInputStream input, int depth) throws IOException {
        int count = readCount(input, "list");
        List<Object> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) result.add(readValue(input, depth));
        return Collections.unmodifiableList(result);
    }

    private static Map<String, Object> readMap(DataInputStream input, int depth) throws IOException {
        int count = readCount(input, "map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = readString(input);
            if (result.containsKey(key)) throw new IllegalArgumentException("graybox state document has duplicate map key " + key);
            result.put(key, readValue(input, depth));
        }
        return Collections.unmodifiableMap(result);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("graybox state document string is too large");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_BYTES) throw new IllegalArgumentException("graybox state document string length is invalid");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new IOException("string is truncated");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readCount(DataInputStream input, String type) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_COLLECTION_ENTRIES) {
            throw new IllegalArgumentException("graybox state document " + type + " length is invalid");
        }
        return count;
    }

    private static double finiteDouble(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("graybox state document rejects non-finite float");
        return value;
    }

    private static void requireDepth(int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("graybox state document nesting is too deep");
    }
}
