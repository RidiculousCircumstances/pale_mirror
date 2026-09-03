package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;

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
            FrontierWorldStateCodec.writeString(output, mobilization.overseerId().value());
            FrontierWorldStateCodec.writeCount(output, mobilization.memberIds().size());
            for (SubjectId member : mobilization.memberIds()) FrontierWorldStateCodec.writeString(output, member.value());
            FrontierWorldStateCodec.writeCount(output, mobilization.releasedMemberIds().size());
            for (SubjectId member : mobilization.releasedMemberIds()) FrontierWorldStateCodec.writeString(output, member.value());
            output.writeBoolean(mobilization.releasingMemberId().isPresent());
            if (mobilization.releasingMemberId().isPresent()) FrontierWorldStateCodec.writeString(output, mobilization.releasingMemberId().orElseThrow().value());
            output.writeByte(mobilization.status().wireTag());
            output.writeBoolean(mobilization.conflictReason().isPresent());
            if (mobilization.conflictReason().isPresent()) output.writeByte(mobilization.conflictReason().orElseThrow().wireTag());
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
            HiveMobilizationStatus status = FrontierWireTags.require(HiveMobilizationStatus.class, input.readUnsignedByte());
            Optional<io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason> reason = input.readBoolean()
                    ? Optional.of(FrontierWireTags.require(io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason.class, input.readUnsignedByte()))
                    : Optional.empty();
            HiveMobilization mobilization = new HiveMobilization(id, hive, nest, task, settlement, overseer, members, released, releasing, status, reason, input.readLong());
            if (mobilizations.put(id, mobilization) != null) throw new IllegalArgumentException("duplicate hive mobilization");
        }
        return Map.copyOf(mobilizations);
    }
}
