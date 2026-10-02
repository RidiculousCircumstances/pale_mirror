package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Shared current-schema encoding; older state layouts are deliberately unsupported. */
final class WorkStateCodec {
    private WorkStateCodec() { }
    static void writeProgress(DataOutputStream out, Optional<WorkProgress> value) throws IOException {
        out.writeBoolean(value.isPresent());
        if (value.isEmpty()) return;
        var work = value.orElseThrow();
        out.writeLong(work.requiredMilliWork()); out.writeLong(work.completedMilliWork());
        out.writeLong(work.evaluatedAtTick()); out.writeInt(work.ratePermille()); out.writeLong(work.activeUntilTick());
    }
    static Optional<WorkProgress> readProgress(DataInputStream in) throws IOException {
        return in.readBoolean() ? Optional.of(new WorkProgress(in.readLong(), in.readLong(), in.readLong(),
                in.readInt(), in.readLong())) : Optional.empty();
    }
    static void writeModifiers(DataOutputStream out, ResidentWorkModifiers value) throws IOException {
        FrontierWorldStateCodec.writeCount(out, value.modifiers().size());
        for (var modifier : value.modifiers().values().stream()
                .sorted(java.util.Comparator.comparing(ResidentWorkModifiers.Modifier::sourceId)).toList()) {
            FrontierWorldStateCodec.writeString(out, modifier.sourceId().value());
            out.writeByte(modifier.capability().wireTag()); out.writeInt(modifier.factorPermille());
        }
    }
    static ResidentWorkModifiers readModifiers(DataInputStream in) throws IOException {
        int count = FrontierWorldStateCodec.readCount(in);
        if (count > 16) throw new IllegalArgumentException("work modifier bound exceeded");
        var values = new java.util.LinkedHashMap<SubjectId, ResidentWorkModifiers.Modifier>();
        for (int index = 0; index < count; index++) {
            var source = new SubjectId(FrontierWorldStateCodec.readString(in));
            var capability = FrontierWireTags.require(HumanCapability.class, in.readUnsignedByte());
            var modifier = new ResidentWorkModifiers.Modifier(source, capability, in.readInt());
            if (values.putIfAbsent(source, modifier) != null) throw new IllegalArgumentException("duplicate work modifier source");
        }
        return new ResidentWorkModifiers(values);
    }
}
