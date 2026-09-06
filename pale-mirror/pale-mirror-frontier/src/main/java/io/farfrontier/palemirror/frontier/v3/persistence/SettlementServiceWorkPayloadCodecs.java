package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
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

/** Stable WAL codecs owned by the settlement-service-work process. */
final class SettlementServiceWorkPayloadCodecs {
    private SettlementServiceWorkPayloadCodecs() { }

    static PayloadCodecs codecs() { return new PayloadCodecs(List.of(new Started(), new Prepared(), new Handoff(),
            new TraversalAdvanced(), new TraversalBlocked(), new Progressed())); }

    private static final class Started implements PayloadCodec {
        @Override public String type() { return "frontier.settlement_service_work_started"; }

        @Override public byte[] encode(FrontierPayload payload) {
            SettlementServiceWorkStarted started = (SettlementServiceWorkStarted) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                FrontierWorldPayloadCodecs.writeSubject(output, started.taskId());
                SettlementServiceWorkStateCodec.writeOne(output, started.work());
                PhysicalIntentPayloadCodec.write(output, started.inputIssueIntent());
                PhysicalIntentPayloadCodec.write(output, started.endpointIntent());
            });
        }

        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                SubjectId task = FrontierWorldPayloadCodecs.readSubject(input).value();
                SettlementServiceWork work = SettlementServiceWorkStateCodec.readOne(input);
                PhysicalIntent inputIssue = PhysicalIntentPayloadCodec.read(input);
                PhysicalIntent endpoint = PhysicalIntentPayloadCodec.read(input);
                return new SettlementServiceWorkStarted(task, work, inputIssue, endpoint);
            });
        }
    }

    private static final class Prepared implements PayloadCodec {
        @Override public String type() { return "frontier.settlement_service_work_scene_lease_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output ->
                writeLease(output, ((SettlementServiceWorkSceneLeasePrepared) payload).lease())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                new SettlementServiceWorkSceneLeasePrepared(readLease(input))); }
    }

    private static final class Handoff implements PayloadCodec {
        @Override public String type() { return "frontier.settlement_service_work_scene_lease_handoff"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            SettlementServiceWorkSceneLeaseHandoff handoff = (SettlementServiceWorkSceneLeaseHandoff) payload;
            writeLease(output, handoff.lease()); writeMembers(output, handoff.ambientMembers());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                new SettlementServiceWorkSceneLeaseHandoff(readLease(input), readMembers(input))); }
    }

    private static final class TraversalAdvanced implements PayloadCodec {
        @Override public String type() { return "frontier.settlement_service_work_traversal_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { SettlementServiceWorkTraversalAdvanced advanced = (SettlementServiceWorkTraversalAdvanced) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> { writeIdLeaseBody(output, advanced.workId(), advanced.leaseId(), advanced.observedWorker()); output.writeShort(advanced.nextCursor()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            IdLeaseBody value = readIdLeaseBody(input); return new SettlementServiceWorkTraversalAdvanced(value.workId(), value.leaseId(), value.body(), input.readUnsignedShort()); }); }
    }

    private static final class TraversalBlocked implements PayloadCodec {
        @Override public String type() { return "frontier.settlement_service_work_traversal_blocked"; }
        @Override public byte[] encode(FrontierPayload payload) { SettlementServiceWorkTraversalBlocked blocked = (SettlementServiceWorkTraversalBlocked) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> { writeIdLeaseBody(output, blocked.workId(), blocked.leaseId(), blocked.observedWorker()); output.writeShort(blocked.blockedNextCursor()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            IdLeaseBody value = readIdLeaseBody(input); return new SettlementServiceWorkTraversalBlocked(value.workId(), value.leaseId(), value.body(), input.readUnsignedShort()); }); }
    }

    private static final class Progressed implements PayloadCodec {
        @Override public String type() { return "frontier.settlement_service_work_progressed"; }
        @Override public byte[] encode(FrontierPayload payload) { SettlementServiceWorkProgressed progressed = (SettlementServiceWorkProgressed) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> { writeIdLeaseBody(output, progressed.workId(), progressed.leaseId(), progressed.observedWorker());
                output.writeByte(progressed.nextPhase().wireTag()); output.writeByte(progressed.completedWorkTicks()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            IdLeaseBody value = readIdLeaseBody(input);
            return new SettlementServiceWorkProgressed(value.workId(), value.leaseId(), value.body(),
                    SettlementServiceWorkPhase.fromWireTag(input.readUnsignedByte()), input.readUnsignedByte()); }); }
    }

    private static void writeIdLeaseBody(DataOutputStream output, SubjectId work, SceneLeaseId lease, BodyPosition body) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, work); FrontierWorldPayloadCodecs.writeString(output, lease.value()); FrontierWorldPayloadCodecs.writeBody(output, body);
    }
    private static IdLeaseBody readIdLeaseBody(DataInputStream input) throws IOException {
        return new IdLeaseBody(FrontierWorldPayloadCodecs.readSubject(input).value(), new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)), FrontierWorldPayloadCodecs.readBody(input));
    }
    private static void writeLease(DataOutputStream output, SceneLease lease) throws IOException {
        if (!(lease.cause() instanceof SettlementServiceWorkSceneCause cause)) throw new IllegalArgumentException("service-work payload requires its typed cause");
        FrontierWorldPayloadCodecs.writeString(output, lease.id().value()); FrontierWorldPayloadCodecs.writeString(output, lease.worldId().value());
        FrontierWorldPayloadCodecs.writeSubject(output, cause.workId());
        FrontierWorldStateCodec.writePosition(output, lease.handoffPosition());
        output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision());
        output.writeByte(lease.status().wireTag());
        output.writeByte(lease.members().size());
        for (SceneMember member : lease.members()) {
            FrontierWorldPayloadCodecs.writeSubject(output, member.actorId()); FrontierWorldPayloadCodecs.writeString(output, member.entityId().toString());
            FrontierWorldPayloadCodecs.writeBody(output, lease.memberPosition(member.actorId()));
        }
        output.writeByte(lease.ambientHandoffActorIds().size());
        for (SubjectId actor : lease.ambientHandoffActorIds().stream().sorted().toList()) FrontierWorldPayloadCodecs.writeSubject(output, actor);
    }
    private static SceneLease readLease(DataInputStream input) throws IOException {
        SceneLeaseId id = new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)); WorldId world = new WorldId(FrontierWorldPayloadCodecs.readString(input));
        SettlementServiceWorkSceneCause cause = new SettlementServiceWorkSceneCause(FrontierWorldPayloadCodecs.readSubject(input).value());
        BlockPosition handoff = FrontierWorldStateCodec.readPosition(input); long instant = input.readLong(), revision = input.readLong();
        SceneLeaseStatus status = FrontierWireTags.require(SceneLeaseStatus.class, input.readUnsignedByte());
        List<SceneMember> members = new ArrayList<>(); Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = FrontierWorldPayloadCodecs.readSubject(input).value(); members.add(new SceneMember(actor, UUID.fromString(FrontierWorldPayloadCodecs.readString(input))));
            positions.put(actor, FrontierWorldPayloadCodecs.readBody(input));
        }
        Set<SubjectId> ambient = new LinkedHashSet<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) ambient.add(FrontierWorldPayloadCodecs.readSubject(input).value());
        return SceneLease.forCause(id, world, cause, handoff, new SimInstant(instant), revision, status, members, positions, ambient, Optional.empty());
    }
    private static void writeMembers(DataOutputStream output, List<SceneMemberPosition> members) throws IOException {
        output.writeByte(members.size());
        for (SceneMemberPosition member : members) { FrontierWorldPayloadCodecs.writeSubject(output, member.actorId()); FrontierWorldPayloadCodecs.writeBody(output, member.body()); output.writeLong(member.health().raw()); }
    }
    private static List<SceneMemberPosition> readMembers(DataInputStream input) throws IOException {
        List<SceneMemberPosition> members = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = FrontierWorldPayloadCodecs.readSubject(input).value();
            BodyPosition body = FrontierWorldPayloadCodecs.readBody(input);
            members.add(new SceneMemberPosition(actor, body, new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())));
        }
        return members;
    }
    private record IdLeaseBody(SubjectId workId, SceneLeaseId leaseId, BodyPosition body) { }
}
