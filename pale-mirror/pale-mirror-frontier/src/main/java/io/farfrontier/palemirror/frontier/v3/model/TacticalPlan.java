package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical operation intent. It only assigns roles to the parent operation's
 * existing members; physical bodies, cargo and custody remain with their owners.
 */
public record TacticalPlan(SubjectId id, SubjectId operationId, SubjectId authorityId, long authorityEpoch, long planEpoch,
                           TacticalPolicyDescriptor policy, TacticalPlanPhase phase, List<SubjectId> objectiveIds,
                           Map<SubjectId, TacticalRole> roles, List<TacticalBehaviour> permittedBehaviours,
                           BlockPosition rendezvous, BlockPosition fallback, BlockPosition retreat) {
    private static final int MAX_OBJECTIVES = 8;

    public TacticalPlan {
        id = Objects.requireNonNull(id, "tactical plan id"); operationId = Objects.requireNonNull(operationId, "tactical operation");
        authorityId = Objects.requireNonNull(authorityId, "tactical authority"); policy = Objects.requireNonNull(policy, "tactical policy");
        phase = Objects.requireNonNull(phase, "tactical phase"); objectiveIds = List.copyOf(objectiveIds);
        roles = Map.copyOf(roles); permittedBehaviours = List.copyOf(permittedBehaviours);
        rendezvous = Objects.requireNonNull(rendezvous, "tactical rendezvous"); fallback = Objects.requireNonNull(fallback, "tactical fallback");
        retreat = Objects.requireNonNull(retreat, "tactical retreat");
        if (authorityEpoch < 0L || planEpoch < 0L || objectiveIds.isEmpty() || objectiveIds.size() > MAX_OBJECTIVES
                || objectiveIds.stream().distinct().count() != objectiveIds.size() || roles.isEmpty() || permittedBehaviours.isEmpty()
                || permittedBehaviours.stream().distinct().count() != permittedBehaviours.size()) {
            throw new IllegalArgumentException("tactical plan retention is invalid");
        }
    }

    public TacticalPlan withPhase(TacticalPlanPhase nextPhase) {
        return new TacticalPlan(id, operationId, authorityId, authorityEpoch, Math.addExact(planEpoch, 1L), policy, nextPhase,
                objectiveIds, roles, permittedBehaviours, rendezvous, fallback, retreat);
    }

    public void validateMembers(List<SubjectId> operationMembers) {
        if (!roles.keySet().equals(java.util.Set.copyOf(operationMembers))) {
            throw new IllegalArgumentException("tactical plan roles must name exactly the parent operation roster");
        }
    }
}
