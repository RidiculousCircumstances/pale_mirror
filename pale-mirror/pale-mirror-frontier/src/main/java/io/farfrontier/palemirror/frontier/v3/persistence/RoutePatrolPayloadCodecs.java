package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** WAL codecs for one persistent route-patrol traversal and its physical finding. */
final class RoutePatrolPayloadCodecs {
    private static final int LEASE_MARKER = 0xfffb;
    private RoutePatrolPayloadCodecs() { }
    static PayloadCodec started() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> writePatrol(output, ((RoutePatrolStarted) payload).patrol())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolStarted(readPatrol(input))); }
    }; }
    static PayloadCodec advanced() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RoutePatrolAdvanced advanced = (RoutePatrolAdvanced) payload; subject(output, advanced.taskId()); subject(output, advanced.actorId());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolAdvanced(subject(input), subject(input))); }
    }; }
    static PayloadCodec obstruction() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_obstruction_confirmed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RoutePatrolObstructionConfirmed confirmed = (RoutePatrolObstructionConfirmed) payload; subject(output, confirmed.taskId()); position(output, confirmed.position());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolObstructionConfirmed(subject(input), position(input))); }
    }; }
    static PayloadCodec failed() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_failed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> subject(output, ((RoutePatrolFailed) payload).taskId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolFailed(subject(input))); }
    }; }
    static PayloadCodec blocked() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_blocked"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> subject(output, ((RoutePatrolBlocked) payload).taskId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolBlocked(subject(input))); }
    }; }
    static PayloadCodec prepared() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_scene_lease_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> writeLease(output, ((RoutePatrolSceneLeasePrepared) payload).lease())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new RoutePatrolSceneLeasePrepared(readLease(input))); }
    }; }
    static PayloadCodec handoff() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_scene_lease_handoff"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RoutePatrolSceneLeaseHandoff handoff = (RoutePatrolSceneLeaseHandoff) payload;
            writeLease(output, handoff.lease()); writeMembers(output, handoff.ambientMembers());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                input -> new RoutePatrolSceneLeaseHandoff(readLease(input), readMembers(input))); }
    }; }
    static PayloadCodec traversalObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.route_patrol_traversal_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            RoutePatrolTraversalObserved observed = (RoutePatrolTraversalObserved) payload;
            subject(output, observed.taskId()); FrontierWorldPayloadCodecs.writeString(output, observed.leaseId().value()); subject(output, observed.actorId());
            output.writeInt(observed.observedBody().x()); output.writeInt(observed.observedBody().y()); output.writeInt(observed.observedBody().z());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                new RoutePatrolTraversalObserved(subject(input), new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)), subject(input),
                        new BodyPosition(input.readInt(), input.readInt(), input.readInt()))); }
    }; }
    private static void writePatrol(DataOutputStream output, RoutePatrol patrol) throws IOException {
        subject(output, patrol.taskId()); subject(output, patrol.settlementId()); RouteUnitManifestCodec.write(output, patrol.unit());
        TraversalTopologyStateCodec.write(output, patrol.inspectionRoute()); PatrolStateCodec.writeAssembly(output, patrol.assembly()); PatrolStateCodec.writeTravel(output, patrol.travel());
        output.writeByte(patrol.status().wireTag()); output.writeBoolean(patrol.obstruction().isPresent());
        if (patrol.obstruction().isPresent()) position(output, patrol.obstruction().orElseThrow());
    }
    private static RoutePatrol readPatrol(DataInputStream input) throws IOException {
        SubjectId task = subject(input), settlement = subject(input); RouteUnitManifest unit = RouteUnitManifestCodec.read(input);
        TraversalTopology inspection = TraversalTopologyStateCodec.read(input); PatrolAssembly assembly = PatrolStateCodec.readAssembly(input); PatrolTravel travel = PatrolStateCodec.readTravel(input);
        int status = input.readUnsignedByte(); Optional<BlockPosition> obstruction = input.readBoolean() ? Optional.of(position(input)) : Optional.empty();
        if (status >= RoutePatrolStatus.values().length) throw new IllegalArgumentException("unknown route patrol status");
        return new RoutePatrol(task, settlement, unit, inspection, assembly, travel, FrontierWireTags.require(RoutePatrolStatus.class, status), obstruction);
    }
    private static void subject(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, id); }
    private static SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
    private static void position(DataOutputStream output, BlockPosition position) throws IOException { output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z()); }
    private static BlockPosition position(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }

    private static void writeLease(DataOutputStream output, SceneLease lease) throws IOException {
        if (!(lease.cause() instanceof RoutePatrolSceneCause cause)) throw new IllegalArgumentException("route-patrol WAL payload requires its typed cause");
        output.writeShort(LEASE_MARKER); FrontierWorldPayloadCodecs.writeString(output, lease.id().value()); FrontierWorldPayloadCodecs.writeString(output, lease.worldId().value());
        subject(output, cause.taskId()); position(output, lease.handoffPosition()); output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag());
        output.writeByte(lease.members().size());
        for (SceneMember member : lease.members()) {
            subject(output, member.actorId()); FrontierWorldPayloadCodecs.writeString(output, member.entityId().toString());
            BodyPosition body = lease.memberPosition(member.actorId()); output.writeInt(body.x()); output.writeInt(body.y()); output.writeInt(body.z());
        }
        output.writeByte(lease.ambientHandoffActorIds().size());
        for (SubjectId actor : lease.ambientHandoffActorIds().stream().sorted().toList()) subject(output, actor);
    }
    private static SceneLease readLease(DataInputStream input) throws IOException {
        if (input.readUnsignedShort() != LEASE_MARKER) throw new IllegalArgumentException("route-patrol payload requires current typed-body envelope");
        SceneLeaseId id = new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)); WorldId world = new WorldId(FrontierWorldPayloadCodecs.readString(input));
        RoutePatrolSceneCause cause = new RoutePatrolSceneCause(subject(input)); BlockPosition handoff = position(input);
        long instant = input.readLong(), revision = input.readLong(); int status = input.readUnsignedByte();
        if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
        List<SceneMember> members = new ArrayList<>(); Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = subject(input); members.add(new SceneMember(actor, UUID.fromString(FrontierWorldPayloadCodecs.readString(input))));
            positions.put(actor, new BodyPosition(input.readInt(), input.readInt(), input.readInt()));
        }
        Set<SubjectId> ambient = new LinkedHashSet<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) ambient.add(subject(input));
        return SceneLease.forCause(id, world, cause, handoff, new SimInstant(instant), revision, FrontierWireTags.require(SceneLeaseStatus.class, status),
                members, positions, ambient, Optional.empty());
    }
    private static void writeMembers(DataOutputStream output, List<SceneMemberPosition> members) throws IOException {
        output.writeByte(members.size());
        for (SceneMemberPosition member : members) {
            subject(output, member.actorId()); BodyPosition body = member.body(); output.writeInt(body.x()); output.writeInt(body.y()); output.writeInt(body.z()); output.writeLong(member.health().raw());
        }
    }
    private static List<SceneMemberPosition> readMembers(DataInputStream input) throws IOException {
        List<SceneMemberPosition> members = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) members.add(new SceneMemberPosition(subject(input),
                new BodyPosition(input.readInt(), input.readInt(), input.readInt()), new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(input.readLong())));
        return members;
    }
}
