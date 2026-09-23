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
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticCategory;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticDisposition;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwner;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticTuple;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticWireTags;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTarget;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
            FrontierWorldStateCodec.writeString(output, intent.causeSubjectId().value()); output.writeByte(intent.roles().schema().wireTag()); FrontierWorldStateCodec.writeCount(output, intent.roles().namedRoles().size());
            for (var entry : intent.roles().namedRoles().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(java.util.Comparator.comparingInt(PhysicalIntentSubjectRole::wireTag))).toList()) {
                output.writeByte(entry.getKey().wireTag()); FrontierWorldStateCodec.writeString(output, entry.getValue().value());
            }
            PhysicalSceneBindingCodec.write(output, intent.roles());
            output.writeLong(intent.origin().x().raw()); output.writeLong(intent.origin().y().raw()); output.writeLong(intent.origin().z().raw());
            output.writeByte(intent.radiusBlocks()); output.writeByte(intent.postcondition().wireTag()); output.writeBoolean(intent.postconditionObservationId().isPresent());
            if (intent.postconditionObservationId().isPresent()) FrontierWorldStateCodec.writeString(output, intent.postconditionObservationId().orElseThrow().value());
            output.writeBoolean(intent.targetSlot().isPresent());
            if (intent.targetSlot().isPresent()) { FrontierWorldStateCodec.writeString(output, intent.targetSlot().orElseThrow().containerId().value()); output.writeByte(intent.targetSlot().orElseThrow().slot()); }
            output.writeBoolean(intent.semanticTarget().isPresent());
            if (intent.semanticTarget().isPresent()) { PhysicalDeltaPayloadCodecs.writeTarget(output, intent.semanticTarget().orElseThrow()); }
            output.writeByte(PhysicalIntentLifecycleOwner.CODEC_VERSION); FrontierWorldStateCodec.writeString(output, intent.lifecycleOwner().stableId());
            output.writeBoolean(intent.diagnostic().isPresent());
            if (intent.diagnostic().isPresent()) writeDiagnostic(output, intent.diagnostic().orElseThrow());
        }
    }

    static Map<PhysicalIntentId, PhysicalIntent> read(DataInputStream input, boolean hasTypedTargetSlot) throws IOException {
        Map<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            PhysicalIntentId id = new PhysicalIntentId(FrontierWorldStateCodec.readString(input)); int kind = input.readUnsignedByte(); int status = input.readUnsignedByte();
            SubjectId cause = new SubjectId(FrontierWorldStateCodec.readString(input)); PhysicalIntentRoleSchema schema = PhysicalIntentRoleSchema.fromWire(input.readUnsignedByte()); Map<PhysicalIntentSubjectRole, SubjectId> roles = new java.util.EnumMap<>(PhysicalIntentSubjectRole.class);
            for (int subject = 0, subjectCount = FrontierWorldStateCodec.readCount(input); subject < subjectCount; subject++) {
                PhysicalIntentSubjectRole role = PhysicalIntentSubjectRole.fromWire(input.readUnsignedByte());
                if (roles.put(role, new SubjectId(FrontierWorldStateCodec.readString(input))) != null) throw new IllegalArgumentException("duplicate physical intent role tag");
            }
            var scene = PhysicalSceneBindingCodec.read(input, schema);
            FixedPosition origin = new FixedPosition(new FixedScalar(input.readLong()), new FixedScalar(input.readLong()), new FixedScalar(input.readLong()));
            int radius = input.readUnsignedByte(); int postcondition = input.readUnsignedByte(); boolean observed = input.readBoolean();
            Optional<PhysicalObservationId> observation = observed ? Optional.of(new PhysicalObservationId(FrontierWorldStateCodec.readString(input))) : Optional.empty();
            Optional<PhysicalContainerSlot> target = hasTypedTargetSlot && input.readBoolean()
                    ? Optional.of(new PhysicalContainerSlot(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readUnsignedByte())) : Optional.empty();
            Optional<PhysicalDeltaSemanticTarget> semanticTarget = input.readBoolean() ? Optional.of(PhysicalDeltaPayloadCodecs.readTarget(input)) : Optional.empty();
            PhysicalIntentLifecycleOwner lifecycleOwner = PhysicalIntentLifecycleOwner.fromWire(input.readUnsignedByte(), FrontierWorldStateCodec.readString(input));
            Optional<DiagnosticTuple> diagnostic = input.readBoolean() ? Optional.of(readDiagnostic(input)) : Optional.empty();
            PhysicalIntent intent = new PhysicalIntent(id, FrontierWireTags.require(PhysicalIntentKind.class, kind), FrontierWireTags.require(PhysicalIntentStatus.class, status), cause,
                    PhysicalIntentRoleBinding.decode(schema, roles, scene), origin, radius, FrontierWireTags.require(PhysicalPostcondition.class, postcondition), observation, target, semanticTarget, lifecycleOwner, diagnostic);
            if (kind >= PhysicalIntentKind.values().length || status >= PhysicalIntentStatus.values().length || postcondition >= PhysicalPostcondition.values().length || intents.put(id, intent) != null) {
                throw new IllegalArgumentException("invalid or duplicate physical intent");
            }
        }
        return intents;
    }
    private static void writeDiagnostic(DataOutputStream output, DiagnosticTuple diagnostic) throws IOException {
        output.writeShort(diagnostic.reason().wireTag()); output.writeByte(diagnostic.category().wireTag());
        output.writeByte(DiagnosticWireTags.ownerTag(diagnostic.owner().kind())); FrontierWorldStateCodec.writeString(output, diagnostic.owner().id().value());
        output.writeByte(DiagnosticWireTags.subjectTag(diagnostic.subject().kind())); FrontierWorldStateCodec.writeString(output, diagnostic.subject().id().value());
        output.writeByte(diagnostic.disposition().wireTag());
    }
    private static DiagnosticTuple readDiagnostic(DataInputStream input) throws IOException {
        return new DiagnosticTuple(DiagnosticWireTags.reason(input.readUnsignedShort()), DiagnosticWireTags.category(input.readUnsignedByte()),
                new DiagnosticOwner(DiagnosticWireTags.ownerKind(input.readUnsignedByte()), new SubjectId(FrontierWorldStateCodec.readString(input))),
                new DiagnosticSubject(DiagnosticWireTags.subjectKind(input.readUnsignedByte()), new SubjectId(FrontierWorldStateCodec.readString(input))),
                DiagnosticWireTags.disposition(input.readUnsignedByte()));
    }
}
