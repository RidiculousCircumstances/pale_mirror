package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandReceipt;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.KernelCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Checksummed, versioned bytes shared by every v3 store implementation. */
public final class FrontierPersistenceCodec {
    private static final int SNAPSHOT_MAGIC = 0x46563353;
    private static final int WAL_MAGIC = 0x46563357;
    /**
     * Fresh-world v3 envelope.  Bumping this rejects an entire old WAL tail before any payload
     * decoder runs, so a recreated world cannot accidentally replay a previous campaign.
     */
    private static final int VERSION = 37;
    private static final int MAX_STATE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRIES = 65_535;

    private FrontierPersistenceCodec() {}

    public static byte[] encodeSnapshot(SnapshotRecord snapshot) {
        return envelope(SNAPSHOT_MAGIC, output -> {
            CheckpointImage image = snapshot.checkpoint();
            writeString(output, image.worldId().value());
            output.writeLong(image.revision().value());
            output.writeLong(image.instant().ticks());
            output.writeLong(snapshot.coveredWalSequence());
            writeBytes(output, image.canonicalState(), MAX_STATE_BYTES);
            writeActions(output, image.schedules());
            if (image.receipts().size() > MAX_ENTRIES) throw new IllegalArgumentException("too many snapshot receipts");
            output.writeShort(image.receipts().size());
            for (CommandReceipt receipt : image.receipts()) {
                writeString(output, receipt.commandId().value());
                output.writeLong(receipt.submittedAt().ticks());
                writeString(output, receipt.transactionId().value());
                output.writeLong(receipt.revision().value());
            }
        });
    }

    public static SnapshotRecord decodeSnapshot(byte[] encoded) {
        return decode(encoded, SNAPSHOT_MAGIC, input -> {
            WorldId world = new WorldId(readString(input));
            Revision revision = new Revision(input.readLong());
            SimInstant instant = new SimInstant(input.readLong());
            long coveredSequence = input.readLong();
            if (coveredSequence < 0L) throw new IllegalArgumentException("negative covered WAL sequence");
            byte[] state = readBytes(input, MAX_STATE_BYTES);
            List<ScheduledAction> schedules = readActions(input);
            int receipts = input.readUnsignedShort();
            List<CommandReceipt> values = new ArrayList<>(receipts);
            for (int index = 0; index < receipts; index++) {
                values.add(new CommandReceipt(new CommandId(readString(input)), new SimInstant(input.readLong()),
                        new TransactionId(readString(input)), new Revision(input.readLong())));
            }
            return new SnapshotRecord(new CheckpointImage(world, revision, instant, state, schedules, values), coveredSequence);
        });
    }

    public static byte[] encodeWal(TransactionRecord transaction, PayloadCodecs codecs) {
        return envelope(WAL_MAGIC, output -> writeBytes(output, KernelCodec.encodeTransaction(transaction, codecs), MAX_STATE_BYTES));
    }

    public static TransactionRecord decodeWal(byte[] encoded, PayloadCodecs codecs) {
        return decode(encoded, WAL_MAGIC, input -> KernelCodec.decodeTransaction(readBytes(input, MAX_STATE_BYTES), codecs));
    }

    private static void writeActions(DataOutputStream output, List<ScheduledAction> actions) throws IOException {
        if (actions.size() > MAX_ENTRIES) throw new IllegalArgumentException("too many snapshot schedules");
        output.writeShort(actions.size());
        for (ScheduledAction action : actions) writeBytes(output, KernelCodec.encodeScheduledAction(action), MAX_STATE_BYTES);
    }

    private static List<ScheduledAction> readActions(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        List<ScheduledAction> actions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) actions.add(KernelCodec.decodeScheduledAction(readBytes(input, MAX_STATE_BYTES)));
        return actions;
    }

    private static byte[] envelope(int magic, Writer writer) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(body)) { writer.write(output); }
            byte[] bytes = body.toByteArray();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(result)) {
                output.writeInt(magic); output.writeByte(VERSION); output.writeInt(bytes.length); output.write(bytes); output.write(digest(bytes));
            }
            return result.toByteArray();
        } catch (IOException error) { throw new IllegalStateException("in-memory persistence encoding failed", error); }
    }

    private static <T> T decode(byte[] encoded, int magic, Reader<T> reader) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != magic) throw new IllegalArgumentException("unknown persistence magic");
            if (input.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Frontier v3 persistence requires a fresh current-schema world");
            byte[] body = readBytes(input, MAX_STATE_BYTES);
            byte[] checksum = input.readNBytes(32);
            if (checksum.length != 32 || !MessageDigest.isEqual(checksum, digest(body)) || input.available() != 0) {
                throw new IllegalArgumentException("invalid persistence checksum or trailing bytes");
            }
            return reader.read(new DataInputStream(new ByteArrayInputStream(body)));
        } catch (IOException error) { throw new IllegalArgumentException("truncated persistence record", error); }
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes, int maximum) throws IOException {
        if (bytes.length > maximum) throw new IllegalArgumentException("persistence value exceeds " + maximum + " bytes");
        output.writeInt(bytes.length); output.write(bytes);
    }

    private static byte[] readBytes(DataInputStream input, int maximum) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximum) throw new IllegalArgumentException("invalid persistence value length");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new IOException("truncated persistence value");
        return bytes;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException { writeBytes(output, value.getBytes(java.nio.charset.StandardCharsets.UTF_8), 256); }
    private static String readString(DataInputStream input) throws IOException { return new String(readBytes(input, 256), java.nio.charset.StandardCharsets.UTF_8); }
    private static byte[] digest(byte[] bytes) { try { return MessageDigest.getInstance("SHA-256").digest(bytes); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    @FunctionalInterface private interface Writer { void write(DataOutputStream output) throws IOException; }
    @FunctionalInterface private interface Reader<T> { T read(DataInputStream input) throws IOException; }
}
