package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationCocoonReleased;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflicted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationReleaseStarted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStarted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Stable payload codecs for the exact cocoon-release state machine. */
final class HiveMobilizationPayloadCodecs {
    private HiveMobilizationPayloadCodecs() { }

    static PayloadCodecs codecs() {
        return new PayloadCodecs(List.of(new StartedCodec(), new ReleaseStartedCodec(), new CocoonReleasedCodec(), new ConflictedCodec()));
    }

    private static final class StartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_mobilization_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeValue(output -> write(output, ((HiveMobilizationStarted) payload).mobilization())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeValue(bytes, input -> new HiveMobilizationStarted(read(input))); }
    }
    private static final class ReleaseStartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_mobilization_release_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeValue(output -> writeSubject(output, ((HiveMobilizationReleaseStarted) payload).mobilizationId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeValue(bytes, input -> new HiveMobilizationReleaseStarted(readSubject(input))); }
    }
    private static final class CocoonReleasedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_mobilization_cocoon_released"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeValue(output -> { HiveMobilizationCocoonReleased released = (HiveMobilizationCocoonReleased) payload;
            writeSubject(output, released.mobilizationId()); writeSubject(output, released.bioformId()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeValue(bytes, input -> new HiveMobilizationCocoonReleased(readSubject(input), readSubject(input))); }
    }
    private static final class ConflictedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_mobilization_conflicted"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeValue(output -> { HiveMobilizationConflicted conflicted = (HiveMobilizationConflicted) payload;
            writeSubject(output, conflicted.mobilizationId()); output.writeByte(conflicted.reason().wireTag()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeValue(bytes, input -> new HiveMobilizationConflicted(readSubject(input),
                FrontierWireTags.require(HiveMobilizationConflictReason.class, input.readUnsignedByte()))); }
    }

    private static void write(DataOutputStream output, HiveMobilization value) throws IOException {
        writeSubject(output, value.id()); writeSubject(output, value.hiveId()); writeSubject(output, value.nestId()); writeSubject(output, value.taskId());
        writeSubject(output, value.settlementId()); writeSubject(output, value.overseerId()); writeSubjects(output, value.memberIds()); writeSubjects(output, value.releasedMemberIds());
        output.writeBoolean(value.releasingMemberId().isPresent()); if (value.releasingMemberId().isPresent()) writeSubject(output, value.releasingMemberId().orElseThrow());
        output.writeByte(value.status().wireTag()); output.writeBoolean(value.conflictReason().isPresent());
        if (value.conflictReason().isPresent()) output.writeByte(value.conflictReason().orElseThrow().wireTag()); output.writeLong(value.startedAt());
    }

    private static HiveMobilization read(DataInputStream input) throws IOException {
        SubjectId id = readSubject(input), hive = readSubject(input), nest = readSubject(input), task = readSubject(input), settlement = readSubject(input), overseer = readSubject(input);
        List<SubjectId> members = readSubjects(input), released = readSubjects(input);
        Optional<SubjectId> releasing = input.readBoolean() ? Optional.of(readSubject(input)) : Optional.empty();
        HiveMobilizationStatus status = FrontierWireTags.require(HiveMobilizationStatus.class, input.readUnsignedByte());
        Optional<HiveMobilizationConflictReason> conflict = input.readBoolean()
                ? Optional.of(FrontierWireTags.require(HiveMobilizationConflictReason.class, input.readUnsignedByte())) : Optional.empty();
        return new HiveMobilization(id, hive, nest, task, settlement, overseer, members, released, releasing, status, conflict, input.readLong());
    }

    private static void writeSubjects(DataOutputStream output, List<SubjectId> values) throws IOException {
        output.writeByte(values.size()); for (SubjectId value : values) writeSubject(output, value);
    }
    private static List<SubjectId> readSubjects(DataInputStream input) throws IOException {
        List<SubjectId> result = new ArrayList<>(); for (int index = 0, count = input.readUnsignedByte(); index < count; index++) result.add(readSubject(input)); return List.copyOf(result);
    }
    private static void writeSubject(DataOutputStream output, SubjectId value) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, value); }
    private static SubjectId readSubject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static byte[] encodeValue(Writer writer) {
        try { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (DataOutputStream output = new DataOutputStream(bytes)) { writer.write(output); } return bytes.toByteArray(); }
        catch (IOException impossible) { throw new IllegalStateException("in-memory hive mobilization payload encoding failed", impossible); }
    }
    private static FrontierPayload decodeValue(byte[] bytes, Reader reader) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) { FrontierPayload value = reader.read(input);
            if (input.available() != 0) throw new IllegalArgumentException("trailing hive mobilization payload bytes"); return value; }
        catch (IOException malformed) { throw new IllegalArgumentException("malformed hive mobilization payload", malformed); }
    }
    @FunctionalInterface private interface Writer { void write(DataOutputStream output) throws IOException; }
    @FunctionalInterface private interface Reader { FrontierPayload read(DataInputStream input) throws IOException; }
}
