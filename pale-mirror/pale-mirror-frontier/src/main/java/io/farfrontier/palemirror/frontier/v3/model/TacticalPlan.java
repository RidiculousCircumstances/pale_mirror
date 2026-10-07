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
        policy = TacticalPolicyRegistry.require(policy);
        TacticalPolicyDescriptor retainedPolicy = policy;
        permittedBehaviours.forEach(behaviour -> TacticalBehaviourRegistry.require(retainedPolicy, behaviour));
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

    /** A retained directive cannot be reused after its owning policy reconsidered. */
    public boolean currentFor(StrategicPlanState plans) {
        Objects.requireNonNull(plans, "strategic plans");
        return plans.currentDecisionAuthority(authorityId, authorityEpoch);
    }


    public static TacticalPlan routePatrol(StrategicTask task, RouteUnitManifest unit, List<BlockPosition> route) {
        Objects.requireNonNull(task, "patrol tactical task"); Objects.requireNonNull(unit, "patrol tactical unit");
        if (task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE || route.size() < 2) {
            throw new IllegalArgumentException("patrol tactical plan requires one exact patrol task and route");
        }
        java.util.Map<SubjectId, TacticalRole> roles = new java.util.LinkedHashMap<>();
        for (SubjectId member : unit.memberIds()) roles.put(member, member.equals(unit.leaderId()) ? TacticalRole.LEADER : TacticalRole.SCOUT);
        String suffix = task.id().value().substring(task.id().value().indexOf(':') + 1).replace(':', '-');
        return new TacticalPlan(new SubjectId("plan:patrol-" + suffix), task.id(), task.authorityId(), task.authorityEpoch(), 0L,
                TacticalPolicyRegistry.ROUTE_PATROL, TacticalPlanPhase.ASSEMBLE, List.of(task.id()), roles,
                List.of(TacticalBehaviour.HOLD_FORMATION, TacticalBehaviour.ADVANCE_CHECKPOINT, TacticalBehaviour.OBSERVE_OBSTRUCTION,
                        TacticalBehaviour.RETREAT_TO_PORT), route.getFirst(), route.getLast(), route.getFirst());
    }

    public static TacticalPlan hiveExpedition(StrategicTask task, SubjectId assaultId, SubjectId overseerId,
                                               List<SubjectId> attackers, List<SubjectId> defenders, BlockPosition rendezvous) {
        Objects.requireNonNull(task, "expedition tactical task"); Objects.requireNonNull(assaultId, "expedition assault");
        if (task.kind() != StrategicTaskKind.ASSAULT_SETTLEMENT || attackers.isEmpty() || defenders.isEmpty()) {
            throw new IllegalArgumentException("expedition tactical plan requires one exact assault roster");
        }
        java.util.Map<SubjectId, TacticalRole> roles = new java.util.LinkedHashMap<>();
        for (SubjectId attacker : attackers) roles.put(attacker, attacker.equals(overseerId) ? TacticalRole.OVERSEER : TacticalRole.ATTACKER);
        for (SubjectId defender : defenders) if (roles.put(defender, TacticalRole.DEFENDER) != null) {
            throw new IllegalArgumentException("expedition sides must be disjoint");
        }
        String suffix = assaultId.value().substring(assaultId.value().indexOf(':') + 1).replace(':', '-');
        return new TacticalPlan(new SubjectId("plan:expedition-" + suffix), assaultId, task.authorityId(), task.authorityEpoch(), 0L,
                TacticalPolicyRegistry.HIVE_EXPEDITION, TacticalPlanPhase.TRAVEL, List.of(task.id()), roles,
                List.of(TacticalBehaviour.HOLD_FORMATION, TacticalBehaviour.ADVANCE_CHECKPOINT, TacticalBehaviour.ENGAGE_WITHIN_ENVELOPE,
                        TacticalBehaviour.RETREAT_TO_PORT), rendezvous, rendezvous, rendezvous);
    }
}
