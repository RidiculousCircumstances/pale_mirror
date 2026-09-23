package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwner;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticTuple;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticWireTags;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTarget;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Optional;

/** Stable WAL representation of a physical intent, including its typed external target. */
final class PhysicalIntentPayloadCodec {
    private PhysicalIntentPayloadCodec() { }

    static void write(DataOutputStream output, PhysicalIntent intent) throws IOException {
        FrontierWorldPayloadCodecs.writeString(output, intent.id().value()); output.writeByte(intent.kind().wireTag()); output.writeByte(intent.status().wireTag());
        FrontierWorldPayloadCodecs.writeSubject(output, intent.causeSubjectId()); output.writeByte(intent.roles().schema().wireTag()); output.writeByte(intent.roles().namedRoles().size());
        for (var entry : intent.roles().namedRoles().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(java.util.Comparator.comparingInt(PhysicalIntentSubjectRole::wireTag))).toList()) {
            output.writeByte(entry.getKey().wireTag()); FrontierWorldPayloadCodecs.writeSubject(output, entry.getValue());
        }
        PhysicalSceneBindingCodec.write(output, intent.roles());
        output.writeLong(intent.origin().x().raw()); output.writeLong(intent.origin().y().raw()); output.writeLong(intent.origin().z().raw());
        output.writeByte(intent.radiusBlocks()); output.writeByte(intent.postcondition().wireTag()); output.writeBoolean(intent.postconditionObservationId().isPresent());
        if (intent.postconditionObservationId().isPresent()) FrontierWorldPayloadCodecs.writeString(output, intent.postconditionObservationId().orElseThrow().value());
        output.writeBoolean(intent.targetSlot().isPresent());
        if (intent.targetSlot().isPresent()) { FrontierWorldPayloadCodecs.writeSubject(output, intent.targetSlot().orElseThrow().containerId()); output.writeByte(intent.targetSlot().orElseThrow().slot()); }
        output.writeBoolean(intent.semanticTarget().isPresent());
        if (intent.semanticTarget().isPresent()) PhysicalDeltaPayloadCodecs.writeTarget(output, intent.semanticTarget().orElseThrow());
        output.writeByte(PhysicalIntentLifecycleOwner.CODEC_VERSION); FrontierWorldPayloadCodecs.writeString(output, intent.lifecycleOwner().stableId());
        output.writeBoolean(intent.diagnostic().isPresent());
        if (intent.diagnostic().isPresent()) writeDiagnostic(output, intent.diagnostic().orElseThrow());
    }

    static PhysicalIntent read(DataInputStream input) throws IOException {
        PhysicalIntentId id = new PhysicalIntentId(FrontierWorldPayloadCodecs.readString(input));
        int kind = input.readUnsignedByte(); int status = input.readUnsignedByte(); FrontierWorldPayloadCodecs.SubjectIdHolder cause = FrontierWorldPayloadCodecs.readSubject(input);
        PhysicalIntentRoleSchema schema = PhysicalIntentRoleSchema.fromWire(input.readUnsignedByte());
        EnumMap<PhysicalIntentSubjectRole, SubjectId> roles = new EnumMap<>(PhysicalIntentSubjectRole.class);
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            PhysicalIntentSubjectRole role = PhysicalIntentSubjectRole.fromWire(input.readUnsignedByte());
            if (roles.put(role, FrontierWorldPayloadCodecs.readSubject(input).value()) != null) throw new IllegalArgumentException("duplicate physical intent role tag");
        }
        var scene = PhysicalSceneBindingCodec.read(input, schema);
        FixedPosition origin = new FixedPosition(new FixedScalar(input.readLong()), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
        int radius = input.readUnsignedByte(); int postcondition = input.readUnsignedByte(); boolean observed = input.readBoolean();
        Optional<PhysicalObservationId> observation = observed ? Optional.of(new PhysicalObservationId(FrontierWorldPayloadCodecs.readString(input))) : Optional.empty();
        Optional<PhysicalContainerSlot> target = input.readBoolean()
                ? Optional.of(new PhysicalContainerSlot(FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedByte())) : Optional.empty();
        Optional<PhysicalDeltaSemanticTarget> semanticTarget = input.readBoolean() ? Optional.of(PhysicalDeltaPayloadCodecs.readTarget(input)) : Optional.empty();
        if (kind >= PhysicalIntentKind.values().length || status >= PhysicalIntentStatus.values().length || postcondition >= PhysicalPostcondition.values().length) {
            throw new IllegalArgumentException("unknown physical intent enum value");
        }
        PhysicalIntentLifecycleOwner lifecycleOwner = PhysicalIntentLifecycleOwner.fromWire(input.readUnsignedByte(), FrontierWorldPayloadCodecs.readString(input));
        Optional<DiagnosticTuple> diagnostic = input.readBoolean() ? Optional.of(readDiagnostic(input)) : Optional.empty();
        return new PhysicalIntent(id, FrontierWireTags.require(PhysicalIntentKind.class, kind), FrontierWireTags.require(PhysicalIntentStatus.class, status),
                cause.value(), PhysicalIntentRoleBinding.decode(schema, roles, scene), origin, radius, FrontierWireTags.require(PhysicalPostcondition.class, postcondition), observation, target, semanticTarget,
                lifecycleOwner, diagnostic);
    }
    private static void writeDiagnostic(DataOutputStream output, DiagnosticTuple diagnostic) throws IOException {
        output.writeShort(diagnostic.reason().wireTag()); output.writeByte(diagnostic.category().wireTag());
        output.writeByte(DiagnosticWireTags.ownerTag(diagnostic.owner().kind())); FrontierWorldPayloadCodecs.writeSubject(output, diagnostic.owner().id());
        output.writeByte(DiagnosticWireTags.subjectTag(diagnostic.subject().kind())); FrontierWorldPayloadCodecs.writeSubject(output, diagnostic.subject().id());
        output.writeByte(diagnostic.disposition().wireTag());
    }
    private static DiagnosticTuple readDiagnostic(DataInputStream input) throws IOException {
        return new DiagnosticTuple(DiagnosticWireTags.reason(input.readUnsignedShort()), DiagnosticWireTags.category(input.readUnsignedByte()),
                new DiagnosticOwner(DiagnosticWireTags.ownerKind(input.readUnsignedByte()), FrontierWorldPayloadCodecs.readSubject(input).value()),
                new DiagnosticSubject(DiagnosticWireTags.subjectKind(input.readUnsignedByte()), FrontierWorldPayloadCodecs.readSubject(input).value()),
                DiagnosticWireTags.disposition(input.readUnsignedByte()));
    }
}
