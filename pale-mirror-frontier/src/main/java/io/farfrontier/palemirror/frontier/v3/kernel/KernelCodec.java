package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Versioned deterministic binary codecs for the kernel values introduced in Wave 1. */
public final class KernelCodec {
    private static final int ACTION_MAGIC = 0x46563341;
    private static final int ACTION_VERSION = 1;
    private static final int MAX_STRING_BYTES = 256;

    private KernelCodec() {
    }

    public static byte[] encodeScheduledAction(ScheduledAction action) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(ACTION_MAGIC);
            output.writeByte(ACTION_VERSION);
            writeString(output, action.id().value());
            output.writeLong(action.dueAt().ticks());
            output.writeInt(action.priority());
            writeString(output, action.subject().value());
            writeString(output, action.kind());
            output.writeInt(action.weight());
            output.flush();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("in-memory scheduled action encoding failed", error);
        }
    }

    public static ScheduledAction decodeScheduledAction(byte[] encoded) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != ACTION_MAGIC) {
                throw new IllegalArgumentException("unknown scheduled action magic");
            }
            if (input.readUnsignedByte() != ACTION_VERSION) {
                throw new IllegalArgumentException("unknown scheduled action version");
            }
            ScheduledAction action = new ScheduledAction(
                    new ScheduleId(readString(input)),
                    new SimInstant(input.readLong()),
                    input.readInt(),
                    new SubjectId(readString(input)),
                    readString(input),
                    input.readInt());
            if (input.available() != 0) {
                throw new IllegalArgumentException("trailing scheduled action bytes");
            }
            return action;
        } catch (IOException error) {
            throw new IllegalArgumentException("truncated scheduled action", error);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("kernel string exceeds " + MAX_STRING_BYTES + " bytes");
        }
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("kernel string exceeds " + MAX_STRING_BYTES + " bytes");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("truncated kernel string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
