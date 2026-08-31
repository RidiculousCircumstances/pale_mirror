package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Atomic canonical mutations spanning COLD combat actors, task ownership and route fate. */
public final class FrontierRouteEngagementStateSupport {
    private FrontierRouteEngagementStateSupport() { }

    public static FrontierWorldState strike(FrontierWorldState state, RouteEngagementStrike strike) {
        RouteEngagement engagement = requireColdEngagement(state, strike.engagementId());
        if (!FrontierSceneAdmission.coldEngagementAvailable(state, engagement)) {
            throw new IllegalArgumentException("COLD strike cannot mutate an ambient-leased combatant");
        }
        if (engagement.nextStrikeEpoch() != strike.epoch()) throw new IllegalArgumentException("COLD strike epoch is stale or replayed");
        boolean hiveTurn = (strike.epoch() & 1) == 0;
        if (hiveTurn != engagement.attackerIds().contains(strike.attackerId())
                || hiveTurn == engagement.attackerIds().contains(strike.targetId())) {
            throw new IllegalArgumentException("COLD strike has invalid side ownership");
        }
        if (!RouteEngagementCombatRules.alive(state, strike.attackerId()) || !RouteEngagementCombatRules.alive(state, strike.targetId())) {
            throw new IllegalArgumentException("COLD strike needs living exact combatants");
        }
        FixedScalar expected = RouteEngagementCombatRules.damage(state, strike.attackerId());
        if (!expected.equals(strike.damage())) throw new IllegalArgumentException("COLD strike damage differs from canonical role rules");
        ActorLocation target = state.actorLocations().get(strike.targetId());
        FixedScalar remaining = target.condition().health().minus(strike.damage());
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(strike.targetId(), remaining.compareTo(FixedScalar.ZERO) <= 0 ? target.deadAt(target.position())
                : new ActorLocation(target.position(), target.condition().withHealth(remaining)));
        StrategicPlanState plans = state.strategicPlans().replaceEngagement(engagement.afterStrike(strike.epoch()));
        return copy(state, actors, state.operations(), plans);
    }

    public static FrontierWorldState resolve(FrontierWorldState state, RouteEngagementResolved resolved) {
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(resolved.engagementId());
        if (engagement == null || engagement.status() == RouteEngagementStatus.RESOLVED || engagement.status() == RouteEngagementStatus.HOT) {
            throw new IllegalArgumentException("route engagement cannot be resolved from its current lifecycle state");
        }
        if (resolved.outcome() != RouteEngagementOutcome.ABORTED && (engagement.status() != RouteEngagementStatus.COLD_COMBAT
                || RouteEngagementCombatRules.outcome(state, engagement) != resolved.outcome())) {
            throw new IllegalArgumentException("route engagement resolution disagrees with exact living actors");
        }
        StrategicPlanState plans = state.strategicPlans().resolveEngagement(engagement.id(), resolved.outcome());
        StrategicTask hiveTask = plans.tasks().get(engagement.taskId());
        plans = plans.transitionTask(hiveTask.id(), resolved.outcome() == RouteEngagementOutcome.HIVE_VICTORY
                ? StrategicTaskStatus.COMPLETED : StrategicTaskStatus.BLOCKED);
        Map<SubjectId, RouteOperation> operations = state.operations();
        if (resolved.outcome() == RouteEngagementOutcome.HIVE_VICTORY) {
            RouteOperation operation = state.operations().get(engagement.operationId());
            StrategicTask delivery = state.strategicPlans().tasks().values().stream()
                    .filter(task -> task.kind() == StrategicTaskKind.DELIVER_BREAD_TO_HIVE && task.status() == StrategicTaskStatus.ACTIVE
                            && task.ownerId().equals(operation.settlementId()))
                    .reduce((left, right) -> { throw new IllegalArgumentException("route engagement has ambiguous active delivery ownership"); })
                    .orElse(null);
            if (delivery != null) plans = plans.transitionTask(delivery.id(), StrategicTaskStatus.BLOCKED);
            operations = new LinkedHashMap<>(operations);
            operations.put(operation.id(), new RouteOperation(operation.id(), operation.settlementId(), operation.cargoId(), operation.destinationId(),
                    operation.participantIds(), operation.route(), operation.routeIndex(), OperationStage.FAILED));
        }
        return copy(state, state.actorLocations(), operations, plans);
    }

    private static RouteEngagement requireColdEngagement(FrontierWorldState state, SubjectId id) {
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(id);
        if (engagement == null || engagement.status() != RouteEngagementStatus.COLD_COMBAT) {
            throw new IllegalArgumentException("route engagement is not in COLD combat");
        }
        return engagement;
    }

    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors,
                                           Map<SubjectId, RouteOperation> operations, StrategicPlanState plans) {
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).operations(operations).strategicPlans(plans));
    }
}
