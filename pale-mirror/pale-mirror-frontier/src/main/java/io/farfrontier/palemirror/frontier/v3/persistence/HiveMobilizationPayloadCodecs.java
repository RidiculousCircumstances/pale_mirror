package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyBlockage;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationCocoonReleased;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationDeparted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflicted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationReleaseStarted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStarted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;

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
        return new PayloadCodecs(List.of(new StartedCodec(), new ReleaseStartedCodec(), new CocoonReleasedCodec(), new AssemblyAdvancedCodec(), new DepartedCodec(), new ConflictedCodec()));
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
    private static final class AssemblyAdvancedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_mobilization_assembly_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeValue(output -> { HiveMobilizationAssemblyAdvanced advanced = (HiveMobilizationAssemblyAdvanced) payload;
            writeSubject(output, advanced.mobilizationId()); writeSubject(output, advanced.bioformId()); output.writeShort(advanced.expectedCursor()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeValue(bytes, input -> new HiveMobilizationAssemblyAdvanced(readSubject(input), readSubject(input), input.readUnsignedShort())); }
    }
    private static final class DepartedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_mobilization_departed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeValue(output -> { HiveMobilizationDeparted departed = (HiveMobilizationDeparted) payload;
            writeSubject(output, departed.mobilizationId()); SettlementAssaultPayloadCodecs.write(output, departed.assault()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeValue(bytes,
                input -> new HiveMobilizationDeparted(readSubject(input), SettlementAssaultPayloadCodecs.read(input))); }
    }
    private static final class ConflictedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.hive_mobilization_conflicted"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeValue(output -> { HiveMobilizationConflicted conflicted = (HiveMobilizationConflicted) payload;
            writeSubject(output, conflicted.mobilizationId()); output.writeByte(conflicted.reason().wireTag()); writeBlockage(output, conflicted.assemblyBlockage()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeValue(bytes, input -> new HiveMobilizationConflicted(readSubject(input),
                FrontierWireTags.require(HiveMobilizationConflictReason.class, input.readUnsignedByte()), readBlockage(input))); }
    }

    private static void write(DataOutputStream output, HiveMobilization value) throws IOException {
        writeSubject(output, value.id()); writeSubject(output, value.hiveId()); writeSubject(output, value.nestId()); writeSubject(output, value.taskId());
        writeSubject(output, value.settlementId()); writeSubject(output, value.sighting().scoutId()); writePosition(output, value.sighting().settlementAnchor()); output.writeLong(value.sighting().observedAt());
        writeSubject(output, value.overseerId()); writeSubjects(output, value.memberIds()); writeSubjects(output, value.releasedMemberIds());
        output.writeBoolean(value.releasingMemberId().isPresent()); if (value.releasingMemberId().isPresent()) writeSubject(output, value.releasingMemberId().orElseThrow());
        output.writeBoolean(value.assembly().isPresent()); if (value.assembly().isPresent()) writeAssembly(output, value.assembly().orElseThrow());
        output.writeByte(value.status().wireTag()); output.writeBoolean(value.conflictReason().isPresent());
        if (value.conflictReason().isPresent()) output.writeByte(value.conflictReason().orElseThrow().wireTag());
        writeBlockage(output, value.assemblyBlockage()); output.writeLong(value.startedAt());
    }

    private static HiveMobilization read(DataInputStream input) throws IOException {
        SubjectId id = readSubject(input), hive = readSubject(input), nest = readSubject(input), task = readSubject(input), settlement = readSubject(input);
        io.farfrontier.palemirror.frontier.v3.model.HiveSettlementKnowledge.Sighting sighting = new io.farfrontier.palemirror.frontier.v3.model.HiveSettlementKnowledge.Sighting(
                settlement, readSubject(input), readPosition(input), input.readLong());
        SubjectId overseer = readSubject(input);
        List<SubjectId> members = readSubjects(input), released = readSubjects(input);
        Optional<SubjectId> releasing = input.readBoolean() ? Optional.of(readSubject(input)) : Optional.empty();
        Optional<HiveTaskAssembly> assembly = input.readBoolean() ? Optional.of(readAssembly(input)) : Optional.empty();
        HiveMobilizationStatus status = FrontierWireTags.require(HiveMobilizationStatus.class, input.readUnsignedByte());
        Optional<HiveMobilizationConflictReason> conflict = input.readBoolean()
                ? Optional.of(FrontierWireTags.require(HiveMobilizationConflictReason.class, input.readUnsignedByte())) : Optional.empty();
        return new HiveMobilization(id, hive, nest, task, settlement, sighting, overseer, members, released, releasing, assembly, status, conflict,
                readBlockage(input), input.readLong());
    }

    private static void writeBlockage(DataOutputStream output, Optional<HiveAssemblyBlockage> blockage) throws IOException {
        output.writeBoolean(blockage.isPresent());
        if (blockage.isEmpty()) return;
        HiveAssemblyBlockage value = blockage.orElseThrow();
        writeSubject(output, value.actorId()); output.writeShort(value.expectedCursor());
        output.writeInt(value.target().x()); output.writeInt(value.target().y()); output.writeInt(value.target().z());
    }

    private static Optional<HiveAssemblyBlockage> readBlockage(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return Optional.empty();
        return Optional.of(new HiveAssemblyBlockage(readSubject(input), input.readUnsignedShort(),
                io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor.at(input.readInt(), input.readInt(), input.readInt())));
    }

    private static void writeAssembly(DataOutputStream output, HiveTaskAssembly assembly) throws IOException {
        writeSubject(output, assembly.ganglionId()); output.writeByte(assembly.members().size());
        for (var entry : assembly.members().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            writeSubject(output, entry.getKey()); TraversalTopologyStateCodec.write(output, entry.getValue().topology()); output.writeShort(entry.getValue().cursor());
        }
    }
    private static HiveTaskAssembly readAssembly(DataInputStream input) throws IOException {
        SubjectId ganglion = readSubject(input); java.util.Map<SubjectId, HiveTaskAssembly.Member> members = new java.util.LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = readSubject(input);
            if (members.put(actor, new HiveTaskAssembly.Member(TraversalTopologyStateCodec.read(input), input.readUnsignedShort())) != null) {
                throw new IllegalArgumentException("duplicate hive assembly member");
            }
        }
        return new HiveTaskAssembly(ganglion, members);
    }

    private static void writeSubjects(DataOutputStream output, List<SubjectId> values) throws IOException {
        output.writeByte(values.size()); for (SubjectId value : values) writeSubject(output, value);
    }
    private static List<SubjectId> readSubjects(DataInputStream input) throws IOException {
        List<SubjectId> result = new ArrayList<>(); for (int index = 0, count = input.readUnsignedByte(); index < count; index++) result.add(readSubject(input)); return List.copyOf(result);
    }
    private static void writePosition(DataOutputStream output, io.farfrontier.palemirror.frontier.v3.model.BlockPosition value) throws IOException {
        output.writeInt(value.x()); output.writeInt(value.y()); output.writeInt(value.z());
    }
    private static io.farfrontier.palemirror.frontier.v3.model.BlockPosition readPosition(DataInputStream input) throws IOException {
        return new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(input.readInt(), input.readInt(), input.readInt());
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
