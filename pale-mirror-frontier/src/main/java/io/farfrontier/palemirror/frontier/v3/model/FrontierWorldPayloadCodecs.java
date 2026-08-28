package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.KernelPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;

import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Complete payload registry for the currently installed v3 world processes. */
public final class FrontierWorldPayloadCodecs {
    private FrontierWorldPayloadCodecs() { }
    public static PayloadCodecs create() {
        return PayloadCodecs.merge(KernelPayloadCodecs.scheduleEffects(), new PayloadCodecs(List.of(
                new InfectionCodec(), new ProductionStartedCodec(), new ProductionCompletedCodec(), new ProductionBlockedCodec())));
    }
    private static final class InfectionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.infection_changed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            InfectionChanged changed = (InfectionChanged) payload;
            return ByteBuffer.allocate(16).putInt(changed.cell().x()).putInt(changed.cell().z()).putLong(changed.intensity().value().raw()).array();
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            if (bytes.length != 16) throw new IllegalArgumentException("malformed infection change payload");
            ByteBuffer input = ByteBuffer.wrap(bytes);
            return new InfectionChanged(new InfectionCell(input.getInt(), input.getInt()), new FixedRatio(new FixedScalar(input.getLong())));
        }
    }
    private static final class ProductionStartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_started"; }
        @Override public byte[] encode(FrontierPayload payload) {
            ProductionStarted started = (ProductionStarted) payload;
            return encodeProduction(output -> { writeJob(output, started.job()); writeSubject(output, started.inputItemId()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                ProductionJob job = readJob(input); SubjectIdHolder item = readSubject(input);
                return new ProductionStarted(job, item.value());
            });
        }
    }
    private static final class ProductionCompletedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_completed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            ProductionCompleted completed = (ProductionCompleted) payload;
            return encodeProduction(output -> {
                writeSubject(output, completed.jobId()); writeSubject(output, completed.output().id()); writeString(output, completed.output().itemKind());
                output.writeByte(completed.output().count());
                if (!(completed.output().custody() instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("production output must have container custody");
                writeSubject(output, slot.containerId()); output.writeByte(slot.slot());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder job = readSubject(input); SubjectIdHolder output = readSubject(input); String kind = readString(input); int count = input.readUnsignedByte();
                SubjectIdHolder container = readSubject(input); int slot = input.readUnsignedByte();
                return new ProductionCompleted(job.value(), new ExactItemStack(output.value(), kind, count, new InventoryCustody.ContainerSlot(container.value(), slot)));
            });
        }
    }
    private static final class ProductionBlockedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.production_blocked"; }
        @Override public byte[] encode(FrontierPayload payload) {
            ProductionBlocked blocked = (ProductionBlocked) payload;
            return encodeProduction(output -> { writeSubject(output, blocked.settlementId()); writeSubject(output, blocked.facilityId()); writeSubject(output, blocked.workId()); output.writeByte(blocked.reason().ordinal()); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return decodeProduction(bytes, input -> {
                SubjectIdHolder settlement = readSubject(input); SubjectIdHolder facility = readSubject(input); SubjectIdHolder work = readSubject(input);
                int ordinal = input.readUnsignedByte();
                if (ordinal >= ProductionBlockReason.values().length) throw new IllegalArgumentException("unknown production block reason");
                return new ProductionBlocked(settlement.value(), facility.value(), work.value(), ProductionBlockReason.values()[ordinal]);
            });
        }
    }

    @FunctionalInterface private interface ProductionEncoder { void write(DataOutputStream output) throws IOException; }
    @FunctionalInterface private interface ProductionDecoder { FrontierPayload read(DataInputStream input) throws IOException; }
    private record SubjectIdHolder(io.farfrontier.palemirror.frontier.v3.api.SubjectId value) { }
    private static byte[] encodeProduction(ProductionEncoder encoder) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) { encoder.write(output); }
            return bytes.toByteArray();
        } catch (IOException error) { throw new IllegalStateException("in-memory production payload encoding failed", error); }
    }
    private static FrontierPayload decodeProduction(byte[] bytes, ProductionDecoder decoder) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            FrontierPayload payload = decoder.read(input);
            if (input.available() != 0) throw new IllegalArgumentException("trailing production payload bytes");
            return payload;
        } catch (IOException error) { throw new IllegalArgumentException("truncated production payload", error); }
    }
    private static void writeJob(DataOutputStream output, ProductionJob job) throws IOException {
        writeSubject(output, job.id()); writeSubject(output, job.settlementId()); writeSubject(output, job.facilityId()); writeSubject(output, job.workerId());
        writeSubject(output, job.consumedItemId()); writeSubject(output, job.outputItemId()); writeString(output, job.outputItemKind()); output.writeByte(job.outputCount());
    }
    private static ProductionJob readJob(DataInputStream input) throws IOException {
        return new ProductionJob(readSubject(input).value(), readSubject(input).value(), readSubject(input).value(), readSubject(input).value(),
                readSubject(input).value(), readSubject(input).value(), readString(input), input.readUnsignedByte());
    }
    private static void writeSubject(DataOutputStream output, io.farfrontier.palemirror.frontier.v3.api.SubjectId value) throws IOException { writeString(output, value.value()); }
    private static SubjectIdHolder readSubject(DataInputStream input) throws IOException { return new SubjectIdHolder(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(readString(input))); }
    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (encoded.length > 256) throw new IllegalArgumentException("production payload field is too long");
        output.writeShort(encoded.length); output.write(encoded);
    }
    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length > 256) throw new IllegalArgumentException("production payload field is too long");
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) throw new IOException("truncated production payload field");
        return new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
    }
}
