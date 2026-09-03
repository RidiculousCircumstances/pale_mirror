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

/** Stable WAL boundary for the one-worker production-work scene. */
final class ProductionWorkScenePayloadCodecs {
    private static final int MARKER = 0xfffc;
    private ProductionWorkScenePayloadCodecs() { }
    static PayloadCodecs codecs() { return new PayloadCodecs(List.of(new Prepared(), new Handoff())); }
    static PayloadCodecs productionEvents() { return new PayloadCodecs(List.of(new Progressed(), new TraversalAdvanced(), new TraversalBlocked(), new Finalized(), new PreparationAborted())); }
    private static final class Progressed implements PayloadCodec {
        @Override public String type() { return "frontier.production_work_progressed"; }
        @Override public byte[] encode(FrontierPayload payload) { ProductionWorkProgressed progressed = (ProductionWorkProgressed) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> { FrontierWorldPayloadCodecs.writeSubject(output, progressed.jobId()); FrontierWorldPayloadCodecs.writeString(output, progressed.leaseId().value());
                FrontierWorldPayloadCodecs.writeBody(output, progressed.observedWorker()); ProductionWorkProgressStateCodec.write(output, progressed.next()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SubjectId job = FrontierWorldPayloadCodecs.readSubject(input).value();
            SceneLeaseId lease = new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input));
            return new ProductionWorkProgressed(job, lease, FrontierWorldPayloadCodecs.readBody(input), ProductionWorkProgressStateCodec.read(input));
        }); }
    }
    private static final class TraversalAdvanced implements PayloadCodec {
        @Override public String type() { return "frontier.production_work_traversal_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) { ProductionWorkTraversalAdvanced advanced = (ProductionWorkTraversalAdvanced) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> { FrontierWorldPayloadCodecs.writeSubject(output, advanced.jobId()); FrontierWorldPayloadCodecs.writeString(output, advanced.leaseId().value());
                FrontierWorldPayloadCodecs.writeBody(output, advanced.observedWorker()); output.writeShort(advanced.nextCursor()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SubjectId job = FrontierWorldPayloadCodecs.readSubject(input).value();
            SceneLeaseId lease = new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input));
            return new ProductionWorkTraversalAdvanced(job, lease, FrontierWorldPayloadCodecs.readBody(input), input.readUnsignedShort());
        }); }
    }
    private static final class TraversalBlocked implements PayloadCodec {
        @Override public String type() { return "frontier.production_work_traversal_blocked"; }
        @Override public byte[] encode(FrontierPayload payload) { ProductionWorkTraversalBlocked blocked = (ProductionWorkTraversalBlocked) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> { FrontierWorldPayloadCodecs.writeSubject(output, blocked.jobId()); FrontierWorldPayloadCodecs.writeString(output, blocked.leaseId().value());
                FrontierWorldPayloadCodecs.writeBody(output, blocked.observedWorker()); output.writeShort(blocked.blockedNextCursor()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SubjectId job = FrontierWorldPayloadCodecs.readSubject(input).value();
            SceneLeaseId lease = new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input));
            return new ProductionWorkTraversalBlocked(job, lease, FrontierWorldPayloadCodecs.readBody(input), input.readUnsignedShort());
        }); }
    }
    private static final class Finalized implements PayloadCodec {
        @Override public String type() { return "frontier.production_work_scene_finalized"; }
        @Override public byte[] encode(FrontierPayload payload) { ProductionWorkSceneFinalized finalized = (ProductionWorkSceneFinalized) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> { FrontierWorldPayloadCodecs.writeString(output, finalized.leaseId().value()); FrontierWorldPayloadCodecs.writeSubject(output, finalized.jobId()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                new ProductionWorkSceneFinalized(new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)), FrontierWorldPayloadCodecs.readSubject(input).value())); }
    }
    private static final class PreparationAborted implements PayloadCodec {
        @Override public String type() { return "frontier.production_work_scene_preparation_aborted"; }
        @Override public byte[] encode(FrontierPayload payload) { ProductionWorkScenePreparationAborted aborted = (ProductionWorkScenePreparationAborted) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> { FrontierWorldPayloadCodecs.writeString(output, aborted.leaseId().value()); FrontierWorldPayloadCodecs.writeSubject(output, aborted.jobId()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input ->
                new ProductionWorkScenePreparationAborted(new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)), FrontierWorldPayloadCodecs.readSubject(input).value())); }
    }
    private static final class Prepared implements PayloadCodec {
        @Override public String type() { return "frontier.production_work_scene_lease_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> writeLease(output, ((ProductionWorkSceneLeasePrepared) payload).lease())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ProductionWorkSceneLeasePrepared(readLease(input))); }
    }
    private static final class Handoff implements PayloadCodec {
        @Override public String type() { return "frontier.production_work_scene_lease_handoff"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            ProductionWorkSceneLeaseHandoff handoff = (ProductionWorkSceneLeaseHandoff) payload; writeLease(output, handoff.lease()); writeMembers(output, handoff.ambientMembers()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ProductionWorkSceneLeaseHandoff(readLease(input), readMembers(input))); }
    }
    private static void writeLease(DataOutputStream output, SceneLease lease) throws IOException {
        if (!(lease.cause() instanceof ProductionWorkSceneCause cause)) throw new IllegalArgumentException("production-work WAL payload requires its typed cause");
        output.writeShort(MARKER); FrontierWorldPayloadCodecs.writeString(output, lease.id().value()); FrontierWorldPayloadCodecs.writeString(output, lease.worldId().value());
        FrontierWorldPayloadCodecs.writeSubject(output, cause.jobId()); writePosition(output, lease.handoffPosition()); output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag());
        output.writeByte(lease.members().size());
        for (SceneMember member : lease.members()) {
            FrontierWorldPayloadCodecs.writeSubject(output, member.actorId()); FrontierWorldPayloadCodecs.writeString(output, member.entityId().toString());
            BodyPosition body = lease.memberPosition(member.actorId()); writePosition(output, new BlockPosition(body.x(), body.y(), body.z()));
        }
        output.writeByte(lease.ambientHandoffActorIds().size()); for (SubjectId actor : lease.ambientHandoffActorIds().stream().sorted().toList()) FrontierWorldPayloadCodecs.writeSubject(output, actor);
    }
    private static SceneLease readLease(DataInputStream input) throws IOException {
        if (input.readUnsignedShort() != MARKER) throw new IllegalArgumentException("production-work payload requires current typed-body envelope");
        SceneLeaseId id = new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)); WorldId world = new WorldId(FrontierWorldPayloadCodecs.readString(input));
        ProductionWorkSceneCause cause = new ProductionWorkSceneCause(FrontierWorldPayloadCodecs.readSubject(input).value()); BlockPosition handoff = readPosition(input);
        long instant = input.readLong(), revision = input.readLong(); int status = input.readUnsignedByte(); if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
        List<SceneMember> members = new ArrayList<>(); Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = FrontierWorldPayloadCodecs.readSubject(input).value();
            members.add(new SceneMember(actor, UUID.fromString(FrontierWorldPayloadCodecs.readString(input))));
            BlockPosition position = readPosition(input); positions.put(actor, new BodyPosition(position.x(), position.y(), position.z()));
        }
        Set<SubjectId> ambient = new LinkedHashSet<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) ambient.add(FrontierWorldPayloadCodecs.readSubject(input).value());
        return SceneLease.forCause(id, world, cause, handoff, new SimInstant(instant), revision, FrontierWireTags.require(SceneLeaseStatus.class, status), members, positions, ambient, Optional.empty());
    }
    private static void writeMembers(DataOutputStream output, List<SceneMemberPosition> members) throws IOException {
        output.writeByte(members.size());
        for (SceneMemberPosition member : members) { FrontierWorldPayloadCodecs.writeSubject(output, member.actorId()); writeBody(output, member.body()); output.writeLong(member.health().raw()); }
    }
    private static List<SceneMemberPosition> readMembers(DataInputStream input) throws IOException {
        List<SceneMemberPosition> members = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) members.add(new SceneMemberPosition(FrontierWorldPayloadCodecs.readSubject(input).value(), readBody(input), new FixedScalar(input.readLong())));
        return members;
    }
    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException { output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z()); }
    private static BlockPosition readPosition(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
    private static void writeBody(DataOutputStream output, BodyPosition body) throws IOException { output.writeInt(body.x()); output.writeInt(body.y()); output.writeInt(body.z()); }
    private static BodyPosition readBody(DataInputStream input) throws IOException { return new BodyPosition(input.readInt(), input.readInt(), input.readInt()); }
}
