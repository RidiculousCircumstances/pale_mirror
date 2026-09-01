package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Stable WAL representation for the typed infirmary-scene boundary. */
final class MedicalTreatmentScenePayloadCodecs {
    private MedicalTreatmentScenePayloadCodecs() { }

    static PayloadCodecs codecs() { return new PayloadCodecs(List.of(new PreparedCodec(), new HandoffCodec())); }

    private static final class PreparedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.medical_treatment_scene_lease_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> writeLease(output, ((MedicalTreatmentSceneLeasePrepared) payload).lease()));
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new MedicalTreatmentSceneLeasePrepared(readLease(input)));
        }
    }

    private static final class HandoffCodec implements PayloadCodec {
        @Override public String type() { return "frontier.medical_treatment_scene_lease_handoff"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                MedicalTreatmentSceneLeaseHandoff handoff = (MedicalTreatmentSceneLeaseHandoff) payload;
                writeLease(output, handoff.lease()); writeMembers(output, handoff.ambientMembers());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new MedicalTreatmentSceneLeaseHandoff(readLease(input), readMembers(input)));
        }
    }

    private static void writeLease(DataOutputStream output, SceneLease lease) throws IOException {
        if (!(lease.cause() instanceof MedicalTreatmentSceneCause cause)) {
            throw new IllegalArgumentException("medical scene WAL payload requires its typed cause");
        }
        FrontierWorldPayloadCodecs.writeString(output, lease.id().value());
        FrontierWorldPayloadCodecs.writeString(output, lease.worldId().value());
        FrontierWorldPayloadCodecs.writeSubject(output, cause.operationId());
        writePosition(output, lease.handoffPosition()); output.writeLong(lease.handoffInstant().ticks());
        output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag()); output.writeByte(lease.members().size());
        for (SceneMember member : lease.members()) {
            FrontierWorldPayloadCodecs.writeSubject(output, member.actorId());
            FrontierWorldPayloadCodecs.writeString(output, member.entityId().toString());
            writePosition(output, lease.memberPosition(member.actorId()));
        }
        output.writeByte(lease.ambientHandoffActorIds().size());
        for (SubjectId actor : lease.ambientHandoffActorIds().stream().sorted().toList()) FrontierWorldPayloadCodecs.writeSubject(output, actor);
    }

    private static SceneLease readLease(DataInputStream input) throws IOException {
        SceneLeaseId id = new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input));
        WorldId world = new WorldId(FrontierWorldPayloadCodecs.readString(input));
        MedicalTreatmentSceneCause cause = new MedicalTreatmentSceneCause(FrontierWorldPayloadCodecs.readSubject(input).value());
        BlockPosition handoff = readPosition(input); long instant = input.readLong(); long revision = input.readLong();
        int status = input.readUnsignedByte();
        List<SceneMember> members = new ArrayList<>(); Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = FrontierWorldPayloadCodecs.readSubject(input).value();
            members.add(new SceneMember(actor, UUID.fromString(FrontierWorldPayloadCodecs.readString(input))));
            positions.put(actor, readPosition(input));
        }
        Set<SubjectId> ambient = new LinkedHashSet<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) ambient.add(FrontierWorldPayloadCodecs.readSubject(input).value());
        return SceneLease.forCause(id, world, cause, handoff, new SimInstant(instant), revision,
                FrontierWireTags.require(SceneLeaseStatus.class, status), members, positions, ambient, Optional.empty());
    }

    private static void writeMembers(DataOutputStream output, List<SceneMemberPosition> members) throws IOException {
        output.writeByte(members.size());
        for (SceneMemberPosition member : members) {
            FrontierWorldPayloadCodecs.writeSubject(output, member.actorId()); writePosition(output, member.position());
            output.writeLong(member.health().raw());
        }
    }

    private static List<SceneMemberPosition> readMembers(DataInputStream input) throws IOException {
        List<SceneMemberPosition> members = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            members.add(new SceneMemberPosition(FrontierWorldPayloadCodecs.readSubject(input).value(), readPosition(input),
                    new FixedScalar(input.readLong())));
        }
        return members;
    }

    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException {
        output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
    }
    private static BlockPosition readPosition(DataInputStream input) throws IOException {
        return new BlockPosition(input.readInt(), input.readInt(), input.readInt());
    }
}
