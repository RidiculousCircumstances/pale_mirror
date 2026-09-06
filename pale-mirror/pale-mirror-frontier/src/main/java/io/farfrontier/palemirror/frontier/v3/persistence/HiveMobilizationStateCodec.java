package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyBlockage;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stable bounded snapshot section for task-owned exact hive mobilizations. */
final class HiveMobilizationStateCodec {
    private HiveMobilizationStateCodec() { }

    static void write(DataOutputStream output, Map<SubjectId, HiveMobilization> mobilizations) throws IOException {
        FrontierWorldStateCodec.writeCount(output, mobilizations.size());
        for (HiveMobilization mobilization : mobilizations.values().stream().sorted(java.util.Comparator.comparing(HiveMobilization::id)).toList()) {
            FrontierWorldStateCodec.writeString(output, mobilization.id().value());
            FrontierWorldStateCodec.writeString(output, mobilization.hiveId().value());
            FrontierWorldStateCodec.writeString(output, mobilization.nestId().value());
            FrontierWorldStateCodec.writeString(output, mobilization.taskId().value());
            FrontierWorldStateCodec.writeString(output, mobilization.settlementId().value());
            FrontierWorldStateCodec.writeString(output, mobilization.sighting().scoutId().value());
            FrontierWorldStateCodec.writePosition(output, mobilization.sighting().settlementAnchor());
            output.writeLong(mobilization.sighting().observedAt());
            FrontierWorldStateCodec.writeString(output, mobilization.overseerId().value());
            FrontierWorldStateCodec.writeCount(output, mobilization.memberIds().size());
            for (SubjectId member : mobilization.memberIds()) FrontierWorldStateCodec.writeString(output, member.value());
            FrontierWorldStateCodec.writeCount(output, mobilization.releasedMemberIds().size());
            for (SubjectId member : mobilization.releasedMemberIds()) FrontierWorldStateCodec.writeString(output, member.value());
            output.writeBoolean(mobilization.releasingMemberId().isPresent());
            if (mobilization.releasingMemberId().isPresent()) FrontierWorldStateCodec.writeString(output, mobilization.releasingMemberId().orElseThrow().value());
            output.writeBoolean(mobilization.assembly().isPresent());
            if (mobilization.assembly().isPresent()) writeAssembly(output, mobilization.assembly().orElseThrow());
            output.writeByte(mobilization.status().wireTag());
            output.writeBoolean(mobilization.conflictReason().isPresent());
            if (mobilization.conflictReason().isPresent()) output.writeByte(mobilization.conflictReason().orElseThrow().wireTag());
            writeBlockage(output, mobilization.assemblyBlockage());
            output.writeLong(mobilization.startedAt());
        }
    }

    static Map<SubjectId, HiveMobilization> read(DataInputStream input) throws IOException {
        Map<SubjectId, HiveMobilization> mobilizations = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input));
            SubjectId hive = new SubjectId(FrontierWorldStateCodec.readString(input));
            SubjectId nest = new SubjectId(FrontierWorldStateCodec.readString(input));
            SubjectId task = new SubjectId(FrontierWorldStateCodec.readString(input));
            SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input));
            io.farfrontier.palemirror.frontier.v3.model.HiveSettlementKnowledge.Sighting sighting = new io.farfrontier.palemirror.frontier.v3.model.HiveSettlementKnowledge.Sighting(
                    settlement, new SubjectId(FrontierWorldStateCodec.readString(input)), FrontierWorldStateCodec.readPosition(input), input.readLong());
            SubjectId overseer = new SubjectId(FrontierWorldStateCodec.readString(input));
            List<SubjectId> members = new ArrayList<>();
            for (int member = 0, memberCount = FrontierWorldStateCodec.readCount(input); member < memberCount; member++) {
                members.add(new SubjectId(FrontierWorldStateCodec.readString(input)));
            }
            List<SubjectId> released = new ArrayList<>();
            for (int member = 0, releasedCount = FrontierWorldStateCodec.readCount(input); member < releasedCount; member++) {
                released.add(new SubjectId(FrontierWorldStateCodec.readString(input)));
            }
            Optional<SubjectId> releasing = input.readBoolean()
                    ? Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input))) : Optional.empty();
            Optional<HiveTaskAssembly> assembly = input.readBoolean() ? Optional.of(readAssembly(input)) : Optional.empty();
            HiveMobilizationStatus status = FrontierWireTags.require(HiveMobilizationStatus.class, input.readUnsignedByte());
            Optional<io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason> reason = input.readBoolean()
                    ? Optional.of(FrontierWireTags.require(io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason.class, input.readUnsignedByte()))
                    : Optional.empty();
            HiveMobilization mobilization = new HiveMobilization(id, hive, nest, task, settlement, sighting, overseer, members, released, releasing, assembly, status, reason,
                    readBlockage(input), input.readLong());
            if (mobilizations.put(id, mobilization) != null) throw new IllegalArgumentException("duplicate hive mobilization");
        }
        return Map.copyOf(mobilizations);
    }

    private static void writeBlockage(DataOutputStream output, Optional<HiveAssemblyBlockage> blockage) throws IOException {
        output.writeBoolean(blockage.isPresent());
        if (blockage.isEmpty()) return;
        HiveAssemblyBlockage value = blockage.orElseThrow();
        FrontierWorldStateCodec.writeString(output, value.actorId().value());
        output.writeShort(value.expectedCursor());
        output.writeInt(value.target().x()); output.writeInt(value.target().y()); output.writeInt(value.target().z());
    }

    private static Optional<HiveAssemblyBlockage> readBlockage(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return Optional.empty();
        return Optional.of(new HiveAssemblyBlockage(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readUnsignedShort(),
                io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor.at(input.readInt(), input.readInt(), input.readInt())));
    }

    private static void writeAssembly(DataOutputStream output, HiveTaskAssembly assembly) throws IOException {
        FrontierWorldStateCodec.writeString(output, assembly.ganglionId().value());
        FrontierWorldStateCodec.writeCount(output, assembly.members().size());
        for (var entry : assembly.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value());
            TraversalTopologyStateCodec.write(output, entry.getValue().topology());
            output.writeShort(entry.getValue().cursor());
        }
    }

    private static HiveTaskAssembly readAssembly(DataInputStream input) throws IOException {
        SubjectId ganglion = new SubjectId(FrontierWorldStateCodec.readString(input));
        Map<SubjectId, HiveTaskAssembly.Member> members = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId actor = new SubjectId(FrontierWorldStateCodec.readString(input));
            if (members.put(actor, new HiveTaskAssembly.Member(TraversalTopologyStateCodec.read(input), input.readUnsignedShort())) != null) {
                throw new IllegalArgumentException("duplicate hive assembly member");
            }
        }
        return new HiveTaskAssembly(ganglion, members);
    }
}
