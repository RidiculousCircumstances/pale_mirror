package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.HiveSettlementKnowledge;
import io.farfrontier.palemirror.frontier.v3.model.ProductionInterrupted;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Stable WAL codec for one defence-caused pre-effect production interruption. */
final class ProductionInterruptionPayloadCodec {
    private ProductionInterruptionPayloadCodec() { }

    static PayloadCodec interrupted() { return new PayloadCodec() {
        @Override public String type() { return "frontier.production_interrupted"; }
        @Override public byte[] encode(FrontierPayload payload) {
            ProductionInterrupted interrupted = (ProductionInterrupted) payload;
            return write(output -> {
                subject(output, interrupted.jobId()); subject(output, interrupted.workerId()); subject(output, interrupted.assaultTaskId());
                HiveSettlementKnowledge.Sighting sighting = interrupted.sighting();
                subject(output, sighting.settlementId()); subject(output, sighting.scoutId());
                output.writeInt(sighting.settlementAnchor().x()); output.writeInt(sighting.settlementAnchor().y()); output.writeInt(sighting.settlementAnchor().z());
                output.writeLong(sighting.observedAt());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) { return read(bytes, input -> {
            SubjectId job = subject(input), worker = subject(input), task = subject(input), settlement = subject(input), scout = subject(input);
            BlockPosition anchor = new BlockPosition(input.readInt(), input.readInt(), input.readInt()); long observedAt = input.readLong();
            return new ProductionInterrupted(job, worker, task, new HiveSettlementKnowledge.Sighting(settlement, scout, anchor, observedAt));
        }); }
    }; }

    private static void subject(DataOutputStream output, SubjectId subject) throws IOException { output.writeUTF(subject.value()); }
    private static SubjectId subject(DataInputStream input) throws IOException { return new SubjectId(input.readUTF()); }
    private static byte[] write(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (DataOutputStream output = new DataOutputStream(bytes)) { writer.write(output); }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException("in-memory production interruption encoding failed", impossible); }
    }
    private static <T> T read(byte[] bytes, Reader<T> reader) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            T value = reader.read(input); if (input.available() != 0) throw new IllegalArgumentException("trailing production interruption payload bytes"); return value;
        } catch (IOException malformed) { throw new IllegalArgumentException("malformed production interruption payload", malformed); }
    }

    @FunctionalInterface private interface Writer { void write(DataOutputStream output) throws IOException; }
    @FunctionalInterface private interface Reader<T> { T read(DataInputStream input) throws IOException; }
}
