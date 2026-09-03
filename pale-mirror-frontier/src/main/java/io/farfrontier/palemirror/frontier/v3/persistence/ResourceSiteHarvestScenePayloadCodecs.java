package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;

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

/** Stable WAL boundary for the one-worker resource-site harvest scene. */
final class ResourceSiteHarvestScenePayloadCodecs {
    private static final int TYPED_BODY_LEASE_MARKER = 0xfffd;
    private ResourceSiteHarvestScenePayloadCodecs() { }

    static PayloadCodecs codecs() { return new PayloadCodecs(List.of(new PreparedCodec(), new HandoffCodec())); }

    private static final class PreparedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.resource_site_harvest_scene_lease_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> writeLease(output, ((ResourceSiteHarvestSceneLeasePrepared) payload).lease()));
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new ResourceSiteHarvestSceneLeasePrepared(readLease(input)));
        }
    }

    private static final class HandoffCodec implements PayloadCodec {
        @Override public String type() { return "frontier.resource_site_harvest_scene_lease_handoff"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResourceSiteHarvestSceneLeaseHandoff handoff = (ResourceSiteHarvestSceneLeaseHandoff) payload;
                writeLease(output, handoff.lease()); writeMembers(output, handoff.ambientMembers());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes,
                    input -> new ResourceSiteHarvestSceneLeaseHandoff(readLease(input), readMembers(input)));
        }
    }

    private static void writeLease(DataOutputStream output, SceneLease lease) throws IOException {
        if (!(lease.cause() instanceof ResourceSiteHarvestSceneCause cause)) {
            throw new IllegalArgumentException("resource-site harvest scene WAL payload requires its typed cause");
        }
        output.writeShort(TYPED_BODY_LEASE_MARKER);
        FrontierWorldPayloadCodecs.writeString(output, lease.id().value()); FrontierWorldPayloadCodecs.writeString(output, lease.worldId().value());
        FrontierWorldPayloadCodecs.writeSubject(output, cause.jobId()); writePosition(output, lease.handoffPosition());
        output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag());
        output.writeByte(lease.members().size());
        for (SceneMember member : lease.members()) {
            FrontierWorldPayloadCodecs.writeSubject(output, member.actorId()); FrontierWorldPayloadCodecs.writeString(output, member.entityId().toString());
            BodyPosition position = lease.memberPosition(member.actorId()); writePosition(output, new BlockPosition(position.x(), position.y(), position.z()));
        }
        output.writeByte(lease.ambientHandoffActorIds().size());
        for (SubjectId actor : lease.ambientHandoffActorIds().stream().sorted().toList()) FrontierWorldPayloadCodecs.writeSubject(output, actor);
    }

    private static SceneLease readLease(DataInputStream input) throws IOException {
        if (input.readUnsignedShort() != TYPED_BODY_LEASE_MARKER) {
            throw new IllegalArgumentException("resource-site harvest scene payload requires the current typed-body envelope");
        }
        SceneLeaseId id = new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)); WorldId world = new WorldId(FrontierWorldPayloadCodecs.readString(input));
        ResourceSiteHarvestSceneCause cause = new ResourceSiteHarvestSceneCause(FrontierWorldPayloadCodecs.readSubject(input).value());
        BlockPosition handoff = readPosition(input); long instant = input.readLong(); long revision = input.readLong(); int status = input.readUnsignedByte();
        if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
        List<SceneMember> members = new ArrayList<>(); Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            SubjectId actor = FrontierWorldPayloadCodecs.readSubject(input).value(); members.add(new SceneMember(actor, UUID.fromString(FrontierWorldPayloadCodecs.readString(input))));
            BlockPosition position = readPosition(input); positions.put(actor, new BodyPosition(position.x(), position.y(), position.z()));
        }
        Set<SubjectId> ambient = new LinkedHashSet<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) ambient.add(FrontierWorldPayloadCodecs.readSubject(input).value());
        return SceneLease.forCause(id, world, cause, handoff, new SimInstant(instant), revision, FrontierWireTags.require(SceneLeaseStatus.class, status),
                members, positions, ambient, Optional.empty());
    }

    private static void writeMembers(DataOutputStream output, List<SceneMemberPosition> members) throws IOException {
        output.writeByte(members.size());
        for (SceneMemberPosition member : members) {
            FrontierWorldPayloadCodecs.writeSubject(output, member.actorId()); writeBody(output, member.body());
            output.writeLong(member.health().raw());
        }
    }

    private static List<SceneMemberPosition> readMembers(DataInputStream input) throws IOException {
        List<SceneMemberPosition> members = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedByte(); index < count; index++) {
            members.add(new SceneMemberPosition(FrontierWorldPayloadCodecs.readSubject(input).value(), readBody(input),
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
    private static void writeBody(DataOutputStream output, BodyPosition body) throws IOException {
        output.writeInt(body.x()); output.writeInt(body.y()); output.writeInt(body.z());
    }
    private static BodyPosition readBody(DataInputStream input) throws IOException {
        return new BodyPosition(input.readInt(), input.readInt(), input.readInt());
    }
}
