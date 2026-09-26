package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

/** Exact scene-lease snapshot section; the root state codec owns only section order. */
final class SceneLeaseStateCodec {
    private SceneLeaseStateCodec() { }

    static void write(DataOutputStream output, Map<SceneLeaseId, SceneLease> leases) throws IOException {
        writeCount(output, leases.size());
        for (SceneLease lease : leases.values().stream().sorted(Comparator.comparing(SceneLease::id)).toList()) {
            writeString(output, lease.id().value()); writeString(output, lease.worldId().value()); SceneCauseStateCodec.write(output, lease.cause());
            writePosition(output, lease.handoffPosition()); output.writeLong(lease.handoffInstant().ticks()); output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag());
            writeCount(output, lease.members().size());
            for (SceneMember member : lease.members()) {
                writeString(output, member.actorId().value());
                writeString(output, member.entityId().toString());
                BodyPosition position = lease.memberPosition(member.actorId());
                writePosition(output, new BlockPosition(position.x(), position.y(), position.z()));
            }
            writeCount(output, lease.ambientHandoffActorIds().size());
            for (SubjectId actor : lease.ambientHandoffActorIds().stream().sorted().toList()) writeString(output, actor.value());
            output.writeBoolean(lease.recoveryEvidence().isPresent());
            if (lease.recoveryEvidence().isPresent()) {
                SceneRecoveryEvidence evidence = lease.recoveryEvidence().orElseThrow();
                writeCount(output, evidence.missingActorIds().size());
                for (SubjectId actor : evidence.missingActorIds().stream().sorted().toList()) writeString(output, actor.value());
                output.writeBoolean(evidence.missingCargoCarrier());
            }
        }
    }
    static Map<SceneLeaseId, SceneLease> read(DataInputStream input) throws IOException {
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>();
        int count = readCount(input);
        for (int index = 0; index < count; index++) {
            SceneLeaseId id = new SceneLeaseId(readString(input)); WorldId world = new WorldId(readString(input));
            SceneCause cause = SceneCauseStateCodec.read(input);
            BlockPosition handoff = readPosition(input);
            long instant = input.readLong(); long revision = input.readLong(); int status = input.readUnsignedByte();
            if (status >= SceneLeaseStatus.values().length) throw new IllegalArgumentException("unknown scene lease status");
            java.util.ArrayList<SceneMember> members = new java.util.ArrayList<>(); Map<SubjectId, BodyPosition> memberPositions = new LinkedHashMap<>();
            for (int member = 0, memberCount = readCount(input); member < memberCount; member++) {
                SubjectId actor = new SubjectId(readString(input)); members.add(new SceneMember(actor, UUID.fromString(readString(input))));
                BlockPosition position = readPosition(input);
                memberPositions.put(actor, new BodyPosition(position.x(), position.y(), position.z()));
            }
            java.util.Set<SubjectId> handoffActors = new java.util.LinkedHashSet<>();
            for (int actor = 0, actorCount = readCount(input); actor < actorCount; actor++) handoffActors.add(new SubjectId(readString(input)));
            java.util.Optional<SceneRecoveryEvidence> recovery = java.util.Optional.empty();
            if (input.readBoolean()) {
                java.util.Set<SubjectId> missing = new java.util.LinkedHashSet<>();
                for (int actor = 0, actorCount = readCount(input); actor < actorCount; actor++) missing.add(new SubjectId(readString(input)));
                recovery = java.util.Optional.of(new SceneRecoveryEvidence(missing, input.readBoolean()));
            }
            SceneLease lease = SceneLease.forCause(id, world, cause, handoff, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(instant), revision,
                    FrontierWireTags.require(SceneLeaseStatus.class, status), members, memberPositions, handoffActors, recovery);
            if (leases.put(id, lease) != null) throw new IllegalArgumentException("duplicate scene lease id");
        }
        return leases;
    }
}
