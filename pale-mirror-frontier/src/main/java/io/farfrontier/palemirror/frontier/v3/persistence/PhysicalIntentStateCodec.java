package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Versioned snapshot representation of physical intents; the owning world codec only chooses the schema version. */
final class PhysicalIntentStateCodec {
    private PhysicalIntentStateCodec() { }

    static void write(DataOutputStream output, Map<PhysicalIntentId, PhysicalIntent> intents) throws IOException {
        FrontierWorldStateCodec.writeCount(output, intents.size());
        for (PhysicalIntent intent : intents.values().stream().sorted(Comparator.comparing(PhysicalIntent::id)).toList()) {
            FrontierWorldStateCodec.writeString(output, intent.id().value()); output.writeByte(intent.kind().wireTag()); output.writeByte(intent.status().wireTag());
            FrontierWorldStateCodec.writeString(output, intent.causeSubjectId().value()); FrontierWorldStateCodec.writeCount(output, intent.subjectIds().size());
            for (SubjectId subject : intent.subjectIds()) FrontierWorldStateCodec.writeString(output, subject.value());
            output.writeLong(intent.origin().x().raw()); output.writeLong(intent.origin().y().raw()); output.writeLong(intent.origin().z().raw());
            output.writeByte(intent.radiusBlocks()); output.writeByte(intent.postcondition().wireTag()); output.writeBoolean(intent.postconditionObservationId().isPresent());
            if (intent.postconditionObservationId().isPresent()) FrontierWorldStateCodec.writeString(output, intent.postconditionObservationId().orElseThrow().value());
            output.writeBoolean(intent.targetSlot().isPresent());
            if (intent.targetSlot().isPresent()) { FrontierWorldStateCodec.writeString(output, intent.targetSlot().orElseThrow().containerId().value()); output.writeByte(intent.targetSlot().orElseThrow().slot()); }
        }
    }

    static Map<PhysicalIntentId, PhysicalIntent> read(DataInputStream input, boolean hasTypedTargetSlot) throws IOException {
        Map<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            PhysicalIntentId id = new PhysicalIntentId(FrontierWorldStateCodec.readString(input)); int kind = input.readUnsignedByte(); int status = input.readUnsignedByte();
            SubjectId cause = new SubjectId(FrontierWorldStateCodec.readString(input)); ArrayList<SubjectId> subjects = new ArrayList<>();
            for (int subject = 0, subjectCount = FrontierWorldStateCodec.readCount(input); subject < subjectCount; subject++) subjects.add(new SubjectId(FrontierWorldStateCodec.readString(input)));
            FixedPosition origin = new FixedPosition(new FixedScalar(input.readLong()), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
            int radius = input.readUnsignedByte(); int postcondition = input.readUnsignedByte(); boolean observed = input.readBoolean();
            Optional<PhysicalObservationId> observation = observed ? Optional.of(new PhysicalObservationId(FrontierWorldStateCodec.readString(input))) : Optional.empty();
            Optional<PhysicalContainerSlot> target = hasTypedTargetSlot && input.readBoolean()
                    ? Optional.of(new PhysicalContainerSlot(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readUnsignedByte())) : Optional.empty();
            PhysicalIntent intent = new PhysicalIntent(id, FrontierWireTags.require(PhysicalIntentKind.class, kind), FrontierWireTags.require(PhysicalIntentStatus.class, status), cause,
                    subjects, origin, radius, FrontierWireTags.require(PhysicalPostcondition.class, postcondition), observation, target);
            if (kind >= PhysicalIntentKind.values().length || status >= PhysicalIntentStatus.values().length || postcondition >= PhysicalPostcondition.values().length || intents.put(id, intent) != null) {
                throw new IllegalArgumentException("invalid or duplicate physical intent");
            }
        }
        return intents;
    }
}
