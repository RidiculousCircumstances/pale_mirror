package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable durable claim preventing concurrent COLD and HOT execution of one route scene. */
public record SceneLease(SceneLeaseId id, WorldId worldId, SubjectId operationId, SubjectId cargoId, BlockPosition handoffPosition,
                         SimInstant handoffInstant, long revision, SceneLeaseStatus status, Optional<SubjectId> engagementId,
                         List<SceneMember> members, Set<SubjectId> ambientHandoffActorIds,
                         Optional<SceneRecoveryEvidence> recoveryEvidence) {
    public SceneLease {
        Objects.requireNonNull(id, "scene lease id");
        Objects.requireNonNull(worldId, "scene lease world");
        Objects.requireNonNull(operationId, "scene lease operation");
        Objects.requireNonNull(cargoId, "scene lease cargo");
        Objects.requireNonNull(handoffPosition, "scene lease handoff position");
        Objects.requireNonNull(handoffInstant, "scene lease handoff instant");
        if (revision < 0) throw new IllegalArgumentException("scene lease revision must be non-negative");
        Objects.requireNonNull(status, "scene lease status"); engagementId = Objects.requireNonNull(engagementId, "scene lease engagement");
        members = List.copyOf(members);
        ambientHandoffActorIds = Set.copyOf(ambientHandoffActorIds);
        recoveryEvidence = Objects.requireNonNull(recoveryEvidence, "scene lease recovery evidence");
        if (members.isEmpty() || members.size() > 32 || members.stream().map(SceneMember::actorId).distinct().count() != members.size()
                || members.stream().map(SceneMember::entityId).distinct().count() != members.size()
                || !members.stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(ambientHandoffActorIds)) {
            throw new IllegalArgumentException("scene lease must have one to thirty-two distinct members and bodies");
        }
        for (SceneMember member : members) {
            if (!deterministicEntityId(worldId, member.actorId()).equals(member.entityId())) {
                throw new IllegalArgumentException("scene member UUID must be deterministic from actor identity");
            }
        }
        if (recoveryEvidence.isPresent() && status != SceneLeaseStatus.UNKNOWN_AFTER_RESTART) {
            throw new IllegalArgumentException("only an unknown scene lease may retain recovery evidence");
        }
    }

    public SceneLease(SceneLeaseId id, WorldId worldId, SubjectId operationId, SubjectId cargoId, BlockPosition handoffPosition,
                      SimInstant handoffInstant, long revision, SceneLeaseStatus status, List<SceneMember> members) {
        this(id, worldId, operationId, cargoId, handoffPosition, handoffInstant, revision, status, Optional.empty(), members, Set.of(), Optional.empty());
    }
    public SceneLease(SceneLeaseId id, WorldId worldId, SubjectId operationId, SubjectId cargoId, BlockPosition handoffPosition,
                      SimInstant handoffInstant, long revision, SceneLeaseStatus status, Optional<SubjectId> engagementId, List<SceneMember> members) {
        this(id, worldId, operationId, cargoId, handoffPosition, handoffInstant, revision, status, engagementId, members, Set.of(), Optional.empty());
    }

    public SceneLease(SceneLeaseId id, WorldId worldId, SubjectId operationId, SubjectId cargoId, BlockPosition handoffPosition,
                      SimInstant handoffInstant, long revision, SceneLeaseStatus status, Optional<SubjectId> engagementId, List<SceneMember> members,
                      Set<SubjectId> ambientHandoffActorIds) {
        this(id, worldId, operationId, cargoId, handoffPosition, handoffInstant, revision, status, engagementId, members, ambientHandoffActorIds, Optional.empty());
    }

    /** One canonical actor retains the same physical identity across ambient and scene leases. */
    public static UUID deterministicEntityId(WorldId worldId, SubjectId actorId) {
        return UUID.nameUUIDFromBytes(("frontier-v3:actor:" + worldId.value() + ":" + actorId.value()).getBytes(StandardCharsets.UTF_8));
    }

    /** The lease is intentionally not part of physical identity. */
    public static UUID deterministicEntityId(WorldId worldId, SceneLeaseId leaseId, SubjectId actorId) {
        Objects.requireNonNull(leaseId, "scene lease id");
        return deterministicEntityId(worldId, actorId);
    }

    public SceneLease withStatus(SceneLeaseStatus nextStatus) {
        return new SceneLease(id, worldId, operationId, cargoId, handoffPosition, handoffInstant, revision, nextStatus, engagementId, members, ambientHandoffActorIds,
                nextStatus == SceneLeaseStatus.UNKNOWN_AFTER_RESTART ? recoveryEvidence : Optional.empty());
    }
    public SceneLease withAmbientHandoff(Set<SubjectId> actorIds) { return new SceneLease(id, worldId, operationId, cargoId, handoffPosition, handoffInstant, revision, status, engagementId, members, actorIds, recoveryEvidence); }
    public SceneLease withRecoveryEvidence(SceneRecoveryEvidence evidence) {
        return new SceneLease(id, worldId, operationId, cargoId, handoffPosition, handoffInstant, revision, status, engagementId, members, ambientHandoffActorIds,
                Optional.of(Objects.requireNonNull(evidence, "scene recovery evidence")));
    }
}
