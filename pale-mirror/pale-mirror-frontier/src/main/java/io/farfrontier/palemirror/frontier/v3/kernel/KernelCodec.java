package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandReceipt;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Versioned deterministic binary codecs for the kernel values introduced in Wave 1. */
public final class KernelCodec {
    private static final int ACTION_MAGIC = 0x46563341;
    private static final int COMMAND_MAGIC = 0x46563343;
    private static final int EVENT_MAGIC = 0x46563345;
    private static final int TRANSACTION_MAGIC = 0x46563354;
    private static final int ACTION_VERSION = 1;
    private static final int TRANSACTION_VERSION = 2;
    private static final int MAX_STRING_BYTES = 256;
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;

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

    public static byte[] encodeCommand(FrontierCommand command, PayloadCodecs codecs) {
        return encode(COMMAND_MAGIC, output -> {
            output.writeInt(command.schemaVersion());
            writeString(output, command.id().value());
            writeString(output, command.worldId().value());
            output.writeLong(command.expectedRevision().value());
            output.writeLong(command.submittedAt().ticks());
            writeString(output, command.actor().value());
            writeCauses(output, command.causes());
            writePayload(output, command.payload(), codecs);
        });
    }

    public static FrontierCommand decodeCommand(byte[] encoded, PayloadCodecs codecs) {
        return decode(encoded, COMMAND_MAGIC, input -> new FrontierCommand(
                input.readInt(), new CommandId(readString(input)), new WorldId(readString(input)),
                new Revision(input.readLong()), new SimInstant(input.readLong()), new SubjectId(readString(input)),
                readCauses(input), readPayload(input, codecs)));
    }

    public static byte[] encodeEvent(FrontierEvent event, PayloadCodecs codecs) {
        return encode(EVENT_MAGIC, output -> writeEvent(output, event, codecs));
    }

    public static FrontierEvent decodeEvent(byte[] encoded, PayloadCodecs codecs) {
        return decode(encoded, EVENT_MAGIC, input -> readEvent(input, codecs));
    }

    public static byte[] encodeTransaction(TransactionRecord transaction, PayloadCodecs codecs) {
        return encode(TRANSACTION_MAGIC, TRANSACTION_VERSION, output -> {
            writeString(output, transaction.id().value());
            writeString(output, transaction.worldId().value());
            output.writeLong(transaction.revision().value());
            output.writeLong(transaction.instant().ticks());
            output.writeShort(transaction.events().size());
            for (FrontierEvent event : transaction.events()) {
                writeEvent(output, event, codecs);
            }
            output.writeBoolean(transaction.acceptedCommandReceipt().isPresent());
            if (transaction.acceptedCommandReceipt().isPresent()) writeReceipt(output, transaction.acceptedCommandReceipt().orElseThrow());
        });
    }

    public static TransactionRecord decodeTransaction(byte[] encoded, PayloadCodecs codecs) {
        return decode(encoded, TRANSACTION_MAGIC, TRANSACTION_VERSION, input -> {
            TransactionId id = new TransactionId(readString(input));
            WorldId world = new WorldId(readString(input));
            Revision revision = new Revision(input.readLong());
            SimInstant instant = new SimInstant(input.readLong());
            int count = input.readUnsignedShort();
            if (count == 0) throw new IllegalArgumentException("transaction contains no events");
            java.util.List<FrontierEvent> events = new java.util.ArrayList<>(count);
            for (int index = 0; index < count; index++) events.add(readEvent(input, codecs));
            java.util.Optional<CommandReceipt> receipt = input.readBoolean() ? java.util.Optional.of(readReceipt(input)) : java.util.Optional.empty();
            return new TransactionRecord(id, world, revision, instant, events, receipt);
        });
    }

    private static void writeReceipt(DataOutputStream output, CommandReceipt receipt) throws IOException {
        writeString(output, receipt.commandId().value());
        output.writeLong(receipt.submittedAt().ticks());
        writeString(output, receipt.transactionId().value());
        output.writeLong(receipt.revision().value());
    }

    private static CommandReceipt readReceipt(DataInputStream input) throws IOException {
        return new CommandReceipt(new CommandId(readString(input)), new SimInstant(input.readLong()),
                new TransactionId(readString(input)), new Revision(input.readLong()));
    }

    private static void writeEvent(DataOutputStream output, FrontierEvent event, PayloadCodecs codecs) throws IOException {
        output.writeInt(event.schemaVersion());
        writeString(output, event.id().value());
        writeString(output, event.transactionId().value());
        writeString(output, event.worldId().value());
        output.writeLong(event.revision().value());
        output.writeLong(event.instant().ticks());
        writeString(output, event.subject().value());
        writeCauses(output, event.causes());
        writePayload(output, event.payload(), codecs);
    }

    private static FrontierEvent readEvent(DataInputStream input, PayloadCodecs codecs) throws IOException {
        return new FrontierEvent(input.readInt(), new EventId(readString(input)), new TransactionId(readString(input)),
                new WorldId(readString(input)), new Revision(input.readLong()), new SimInstant(input.readLong()),
                new SubjectId(readString(input)), readCauses(input), readPayload(input, codecs));
    }

    private static void writeCauses(DataOutputStream output, CauseChain causes) throws IOException {
        output.writeByte(causes.commands().size());
        for (CommandId command : causes.commands()) writeString(output, command.value());
    }

    private static CauseChain readCauses(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte();
        java.util.List<CommandId> commands = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) commands.add(new CommandId(readString(input)));
        return new CauseChain(commands);
    }

    private static void writePayload(DataOutputStream output, FrontierPayload payload, PayloadCodecs codecs) throws IOException {
        writeString(output, payload.type());
        byte[] bytes = codecs.encode(payload);
        if (bytes.length > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static FrontierPayload readPayload(DataInputStream input, PayloadCodecs codecs) throws IOException {
        String type = readString(input);
        int length = input.readInt();
        if (length < 0 || length > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("invalid payload length: " + length);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new IOException("truncated payload");
        return codecs.decode(type, bytes);
    }

    private static byte[] encode(int magic, Encoder body) { return encode(magic, ACTION_VERSION, body); }

    private static byte[] encode(int magic, int version, Encoder body) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(magic);
            output.writeByte(version);
            body.write(output);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("in-memory kernel encoding failed", error);
        }
    }

    private static <T> T decode(byte[] encoded, int magic, Decoder<T> body) { return decode(encoded, magic, ACTION_VERSION, body); }

    private static <T> T decode(byte[] encoded, int magic, int version, Decoder<T> body) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != magic) throw new IllegalArgumentException("unknown kernel envelope magic");
            if (input.readUnsignedByte() != version) throw new IllegalArgumentException("unknown kernel envelope version");
            T result = body.read(input);
            if (input.available() != 0) throw new IllegalArgumentException("trailing kernel envelope bytes");
            return result;
        } catch (IOException error) {
            throw new IllegalArgumentException("truncated kernel envelope", error);
        }
    }

    @FunctionalInterface private interface Encoder { void write(DataOutputStream output) throws IOException; }
    @FunctionalInterface private interface Decoder<T> { T read(DataInputStream input) throws IOException; }

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
