package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable durable claim preventing concurrent COLD and HOT execution of one typed scene. */
public record SceneLease(SceneLeaseId id, WorldId worldId, SceneCause cause, BlockPosition handoffPosition,
                         SimInstant handoffInstant, long revision, SceneLeaseStatus status, List<SceneMember> members,
                         Map<SubjectId, BodyPosition> memberPositions, Set<SubjectId> ambientHandoffActorIds,
                         Optional<SceneRecoveryEvidence> recoveryEvidence) {
    public SceneLease {
        Objects.requireNonNull(id, "scene lease id");
        Objects.requireNonNull(worldId, "scene lease world");
        Objects.requireNonNull(handoffPosition, "scene lease handoff position");
        Objects.requireNonNull(handoffInstant, "scene lease handoff instant");
        if (revision < 0) throw new IllegalArgumentException("scene lease revision must be non-negative");
        Objects.requireNonNull(status, "scene lease status");
        cause = Objects.requireNonNull(cause, "scene cause");
        cause.validateLeaseStatus(status);
        members = List.copyOf(members);
        Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
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

    public static SceneLease atExactPositions(SceneLeaseId id, WorldId worldId, SubjectId operationId, SubjectId cargoId,
                                              BlockPosition handoffPosition, BlockPosition cargoPosition, SimInstant handoffInstant, long revision,
                                              SceneLeaseStatus status, Optional<SubjectId> engagementId, List<SceneMember> members,
                                              Map<SubjectId, BodyPosition> memberPositions) {
        return new SceneLease(id, worldId, new LogisticsSceneCause(operationId, cargoId, engagementId, cargoPosition,
                CargoProjectionRetirement.Disposition.REMOVE_PROJECTION), handoffPosition, handoffInstant,
                revision, status, members, memberPositions, Set.of(), Optional.empty());
    }

    /** New scene families must use an explicit typed cause; their owner validates it before preparation. */
    public static SceneLease forCause(SceneLeaseId id, WorldId worldId, SceneCause cause, BlockPosition handoffPosition,
                                      SimInstant handoffInstant, long revision, SceneLeaseStatus status, List<SceneMember> members,
                                      Map<SubjectId, BodyPosition> memberPositions, Set<SubjectId> ambientHandoffActorIds,
                                      Optional<SceneRecoveryEvidence> recoveryEvidence) {
        return new SceneLease(id, worldId, cause, handoffPosition, handoffInstant, revision, status, members, memberPositions,
                ambientHandoffActorIds, recoveryEvidence);
    }

    /** One canonical actor retains the same physical identity across ambient and scene leases. */
    public static UUID deterministicEntityId(WorldId worldId, SubjectId actorId) {
        return io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(worldId, actorId);
    }

    /** The lease is intentionally not part of physical identity. */
    public static UUID deterministicEntityId(WorldId worldId, SceneLeaseId leaseId, SubjectId actorId) {
        Objects.requireNonNull(leaseId, "scene lease id");
        return deterministicEntityId(worldId, actorId);
    }

    public SceneLease withStatus(SceneLeaseStatus nextStatus) {
        return new SceneLease(id, worldId, cause, handoffPosition, handoffInstant, revision, nextStatus, members, memberPositions,
                ambientHandoffActorIds, nextStatus == SceneLeaseStatus.UNKNOWN_AFTER_RESTART ? recoveryEvidence : Optional.empty());
    }
    /** The accepted handoff owns this irreversible decision, independent of later item custody. */
    SceneLease withReleasedCargoCarrier() {
        var logistics = FrontierSceneBehaviors.logistics(this);
        if (status != SceneLeaseStatus.HOT) throw new IllegalArgumentException("only HOT carrier can be released");
        return new SceneLease(id, worldId, new LogisticsSceneCause(logistics.operationId(), logistics.cargoId(),
                logistics.engagementId(), logistics.cargoPosition(), CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY),
                handoffPosition, handoffInstant, revision, SceneLeaseStatus.DRAINING, members, memberPositions,
                ambientHandoffActorIds, Optional.empty());
    }
    /** Work may be suspended while this lease still owns physical consequences. */
    public boolean retainsMemberCustody(SubjectId actorId) {
        return status != SceneLeaseStatus.CLOSED && memberPositions.containsKey(actorId);
    }
    public SceneLease withAmbientHandoff(Set<SubjectId> actorIds) {
        return new SceneLease(id, worldId, cause, handoffPosition, handoffInstant, revision, status, members, memberPositions, actorIds, recoveryEvidence);
    }
    public SceneLease withMemberPositions(Map<SubjectId, BodyPosition> positions) {
        return new SceneLease(id, worldId, cause, handoffPosition, handoffInstant, revision, status, members, positions, ambientHandoffActorIds, recoveryEvidence);
    }
    /**
     * Before an ambient hand-off is accepted, an unstarted process may compile its first
     * retained surface from the exact observed body.  Keep the pre-acceptance lease anchor in
     * lockstep with that durable start; this does not alter an admitted scene's identity,
     * instant, members, or progress.
     */
    public SceneLease withHandoffPosition(BlockPosition position) {
        return new SceneLease(id, worldId, cause, Objects.requireNonNull(position, "scene lease handoff position"), handoffInstant, revision,
                status, members, memberPositions, ambientHandoffActorIds, recoveryEvidence);
    }
    /**
     * Advances a non-combat HOT logistics scene at the same durable grid checkpoint as its
     * operation.  The lease keeps its identity and body UUIDs; only its current recovery anchor
     * moves, so a return/restart never seeks the caravan at a stale segment origin.
     */
    public SceneLease rebaseHotOperationTravel(OperationTravel prior, OperationTravel next) {
        Objects.requireNonNull(prior, "prior operation travel"); Objects.requireNonNull(next, "next operation travel");
        LogisticsSceneCause logistics = FrontierSceneBehaviors.logistics(this);
        if (status != SceneLeaseStatus.HOT || logistics.engagementId().isPresent() || !memberPositions.equals(prior.formation())
                || !logistics.cargoPosition().equals(prior.cargoAnchor().surface().support()) || !next.isExactHotAdvanceFrom(prior)) {
            throw new IllegalArgumentException("HOT logistics scene does not match its current operation travel");
        }
        return new SceneLease(id, worldId,
                new LogisticsSceneCause(logistics.operationId(), logistics.cargoId(), logistics.engagementId(), next.cargoAnchor().surface().support(), logistics.carrierDisposition()),
                next.currentPosition(), handoffInstant, revision, status, members, next.formation(), ambientHandoffActorIds, recoveryEvidence);
    }
    public SceneLease withRecoveryEvidence(SceneRecoveryEvidence evidence) {
        return new SceneLease(id, worldId, cause, handoffPosition, handoffInstant, revision, status, members, memberPositions,
                ambientHandoffActorIds, Optional.of(Objects.requireNonNull(evidence, "scene recovery evidence")));
    }
    public BodyPosition memberPosition(SubjectId actorId) { return memberPositions.get(Objects.requireNonNull(actorId, "scene actor")); }
    /** Converts current-domain support cells supplied by scene candidates to exact body cells. */
    public static Map<SubjectId, BodyPosition> bodiesAboveSupportCells(Map<SubjectId, BlockPosition> supports) {
        Map<SubjectId, BodyPosition> result = new LinkedHashMap<>();
        supports.forEach((actor, support) -> result.put(actor, BodyPosition.aboveSupportCell(support)));
        return Map.copyOf(result);
    }

}
