package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable durable claim preventing concurrent COLD and HOT execution of one route scene. */
public record SceneLease(SceneLeaseId id, WorldId worldId, SubjectId operationId, SubjectId cargoId, BlockPosition handoffPosition,
                         BlockPosition cargoPosition,
                         SimInstant handoffInstant, long revision, SceneLeaseStatus status, Optional<SubjectId> engagementId,
                         List<SceneMember> members, Map<SubjectId, BlockPosition> memberPositions, Set<SubjectId> ambientHandoffActorIds,
                         Optional<SceneRecoveryEvidence> recoveryEvidence) {
    public SceneLease {
        Objects.requireNonNull(id, "scene lease id");
        Objects.requireNonNull(worldId, "scene lease world");
        Objects.requireNonNull(operationId, "scene lease operation");
        Objects.requireNonNull(cargoId, "scene lease cargo");
        Objects.requireNonNull(handoffPosition, "scene lease handoff position");
        Objects.requireNonNull(cargoPosition, "scene lease cargo position");
        Objects.requireNonNull(handoffInstant, "scene lease handoff instant");
        if (revision < 0) throw new IllegalArgumentException("scene lease revision must be non-negative");
        Objects.requireNonNull(status, "scene lease status"); engagementId = Objects.requireNonNull(engagementId, "scene lease engagement");
        members = List.copyOf(members);
        Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>();
        memberPositions.forEach((actor, position) -> positions.put(Objects.requireNonNull(actor, "scene member position actor"),
                Objects.requireNonNull(position, "scene member position")));
        memberPositions = Map.copyOf(positions);
        ambientHandoffActorIds = Set.copyOf(ambientHandoffActorIds);
        recoveryEvidence = Objects.requireNonNull(recoveryEvidence, "scene lease recovery evidence");
        if (members.isEmpty() || members.size() > 32 || members.stream().map(SceneMember::actorId).distinct().count() != members.size()
                || members.stream().map(SceneMember::entityId).distinct().count() != members.size()
                || !members.stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).containsAll(ambientHandoffActorIds)
                || !memberPositions.keySet().equals(members.stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()))) {
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
        this(id, worldId, operationId, cargoId, handoffPosition, handoffPosition, handoffInstant, revision, status, Optional.empty(), members, positions(members, handoffPosition), Set.of(), Optional.empty());
    }
    public SceneLease(SceneLeaseId id, WorldId worldId, SubjectId operationId, SubjectId cargoId, BlockPosition handoffPosition,
                      SimInstant handoffInstant, long revision, SceneLeaseStatus status, Optional<SubjectId> engagementId, List<SceneMember> members) {
        this(id, worldId, operationId, cargoId, handoffPosition, handoffPosition, handoffInstant, revision, status, engagementId, members, positions(members, handoffPosition), Set.of(), Optional.empty());
    }

    public SceneLease(SceneLeaseId id, WorldId worldId, SubjectId operationId, SubjectId cargoId, BlockPosition handoffPosition,
                      SimInstant handoffInstant, long revision, SceneLeaseStatus status, Optional<SubjectId> engagementId, List<SceneMember> members,
                      Set<SubjectId> ambientHandoffActorIds) {
        this(id, worldId, operationId, cargoId, handoffPosition, handoffPosition, handoffInstant, revision, status, engagementId, members, positions(members, handoffPosition), ambientHandoffActorIds, Optional.empty());
    }

    public static SceneLease atExactPositions(SceneLeaseId id, WorldId worldId, SubjectId operationId, SubjectId cargoId,
                                              BlockPosition handoffPosition, BlockPosition cargoPosition, SimInstant handoffInstant, long revision,
                                              SceneLeaseStatus status, Optional<SubjectId> engagementId, List<SceneMember> members,
                                              Map<SubjectId, BlockPosition> memberPositions) {
        return new SceneLease(id, worldId, operationId, cargoId, handoffPosition, cargoPosition, handoffInstant, revision, status, engagementId,
                members, memberPositions, Set.of(), Optional.empty());
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
        return new SceneLease(id, worldId, operationId, cargoId, handoffPosition, cargoPosition, handoffInstant, revision, nextStatus, engagementId, members, memberPositions, ambientHandoffActorIds,
                nextStatus == SceneLeaseStatus.UNKNOWN_AFTER_RESTART ? recoveryEvidence : Optional.empty());
    }
    public SceneLease withAmbientHandoff(Set<SubjectId> actorIds) {
        return new SceneLease(id, worldId, operationId, cargoId, handoffPosition, cargoPosition, handoffInstant,
                revision, status, engagementId, members, memberPositions, actorIds, recoveryEvidence);
    }
    public SceneLease withMemberPositions(Map<SubjectId, BlockPosition> positions) {
        return new SceneLease(id, worldId, operationId, cargoId, handoffPosition, cargoPosition, handoffInstant, revision, status, engagementId, members, positions,
                ambientHandoffActorIds, recoveryEvidence);
    }
    /**
     * Advances a non-combat HOT logistics scene at the same durable grid checkpoint as its
     * operation.  The lease keeps its identity and body UUIDs; only its current recovery anchor
     * moves, so a return/restart never seeks the caravan at a stale segment origin.
     */
    public SceneLease rebaseHotOperationTravel(OperationTravel prior, OperationTravel next) {
        Objects.requireNonNull(prior, "prior operation travel"); Objects.requireNonNull(next, "next operation travel");
        if (status != SceneLeaseStatus.HOT || engagementId.isPresent() || !memberPositions.equals(prior.formation())
                || !cargoPosition.equals(prior.cargoAnchor()) || !next.isExactHotAdvanceFrom(prior)) {
            throw new IllegalArgumentException("HOT logistics scene does not match its current operation travel");
        }
        return new SceneLease(id, worldId, operationId, cargoId, next.currentPosition(), next.cargoAnchor(), handoffInstant, revision,
                status, engagementId, members, next.formation(), ambientHandoffActorIds, recoveryEvidence);
    }
    public SceneLease withRecoveryEvidence(SceneRecoveryEvidence evidence) {
        return new SceneLease(id, worldId, operationId, cargoId, handoffPosition, cargoPosition, handoffInstant, revision, status, engagementId, members, memberPositions, ambientHandoffActorIds,
                Optional.of(Objects.requireNonNull(evidence, "scene recovery evidence")));
    }
    public BlockPosition memberPosition(SubjectId actorId) { return memberPositions.get(Objects.requireNonNull(actorId, "scene actor")); }
    private static Map<SubjectId, BlockPosition> positions(List<SceneMember> members, BlockPosition handoffPosition) {
        Map<SubjectId, BlockPosition> result = new LinkedHashMap<>();
        members.forEach(member -> result.put(member.actorId(), handoffPosition));
        return Map.copyOf(result);
    }
}
