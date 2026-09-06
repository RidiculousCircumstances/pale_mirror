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
import java.util.Optional;

/** Stable WAL representation of a physical intent, including its typed external target. */
final class PhysicalIntentPayloadCodec {
    private PhysicalIntentPayloadCodec() { }

    static void write(DataOutputStream output, PhysicalIntent intent) throws IOException {
        FrontierWorldPayloadCodecs.writeString(output, intent.id().value()); output.writeByte(intent.kind().wireTag()); output.writeByte(intent.status().wireTag());
        FrontierWorldPayloadCodecs.writeSubject(output, intent.causeSubjectId()); output.writeByte(intent.subjectIds().size());
        for (SubjectId subject : intent.subjectIds()) FrontierWorldPayloadCodecs.writeSubject(output, subject);
        output.writeLong(intent.origin().x().raw()); output.writeLong(intent.origin().y().raw()); output.writeLong(intent.origin().z().raw());
        output.writeByte(intent.radiusBlocks()); output.writeByte(intent.postcondition().wireTag()); output.writeBoolean(intent.postconditionObservationId().isPresent());
        if (intent.postconditionObservationId().isPresent()) FrontierWorldPayloadCodecs.writeString(output, intent.postconditionObservationId().orElseThrow().value());
        output.writeBoolean(intent.targetSlot().isPresent());
        if (intent.targetSlot().isPresent()) { FrontierWorldPayloadCodecs.writeSubject(output, intent.targetSlot().orElseThrow().containerId()); output.writeByte(intent.targetSlot().orElseThrow().slot()); }
    }

    static PhysicalIntent read(DataInputStream input) throws IOException {
        PhysicalIntentId id = new PhysicalIntentId(FrontierWorldPayloadCodecs.readString(input));
        int kind = input.readUnsignedByte(); int status = input.readUnsignedByte(); FrontierWorldPayloadCodecs.SubjectIdHolder cause = FrontierWorldPayloadCodecs.readSubject(input);
        ArrayList<SubjectId> subjects = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) subjects.add(FrontierWorldPayloadCodecs.readSubject(input).value());
        FixedPosition origin = new FixedPosition(new FixedScalar(input.readLong()), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
        int radius = input.readUnsignedByte(); int postcondition = input.readUnsignedByte(); boolean observed = input.readBoolean();
        Optional<PhysicalObservationId> observation = observed ? Optional.of(new PhysicalObservationId(FrontierWorldPayloadCodecs.readString(input))) : Optional.empty();
        Optional<PhysicalContainerSlot> target = input.available() > 0 && input.readBoolean()
                ? Optional.of(new PhysicalContainerSlot(FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedByte())) : Optional.empty();
        if (kind >= PhysicalIntentKind.values().length || status >= PhysicalIntentStatus.values().length || postcondition >= PhysicalPostcondition.values().length) {
            throw new IllegalArgumentException("unknown physical intent enum value");
        }
        return new PhysicalIntent(id, FrontierWireTags.require(PhysicalIntentKind.class, kind), FrontierWireTags.require(PhysicalIntentStatus.class, status),
                cause.value(), subjects, origin, radius, FrontierWireTags.require(PhysicalPostcondition.class, postcondition), observation, target);
    }
}
