package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Validates cross-aggregate ownership of durable route engagements. */
final class FrontierRouteEngagementSupport {
    private FrontierRouteEngagementSupport() { }

    static void validate(FrontierBootstrap bootstrap, HiveColony hiveColony, Map<SubjectId, ActorLocation> actorLocations,
                         Map<SubjectId, RouteOperation> operations, StrategicPlanState strategicPlans) {
        for (StrategicTask task : strategicPlans.tasks().values()) {
            if (task.kind() == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION && (!bootstrap.hive().id().equals(task.ownerId())
                    || task.operationTarget().isEmpty() || !operations.containsKey(task.operationTarget().orElseThrow()))) {
                throw new IllegalArgumentException("route-intercept task must retain one current hive-owned operation target");
            }
        }
        Set<SubjectId> hiveBioforms = new HashSet<>();
        bootstrap.hive().bioforms().forEach(bioform -> hiveBioforms.add(bioform.id()));
        hiveBioforms.addAll(hiveColony.spawnedBioforms().keySet());
        Set<SubjectId> activeOperations = new HashSet<>();
        Set<SubjectId> activeAttackers = new HashSet<>();
        for (RouteEngagement engagement : strategicPlans.routeEngagements().values()) {
            StrategicTask task = strategicPlans.tasks().get(engagement.taskId());
            if (!bootstrap.hive().id().equals(engagement.hiveId()) || task == null || !task.ownerId().equals(engagement.hiveId())) {
                throw new IllegalArgumentException("route engagement must be owned by its hive task");
            }
            RouteOperation operation = operations.get(engagement.operationId());
            if (operation == null) throw new IllegalArgumentException("route engagement must target one canonical route operation");
            FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), engagement.intercept());
            boolean active = engagement.status() != RouteEngagementStatus.RESOLVED;
            if (active && (operation.stage() != OperationStage.EN_ROUTE || !activeOperations.add(operation.id()))) {
                throw new IllegalArgumentException("active route engagement must uniquely target an en-route operation");
            }
            for (EngagementAttacker attacker : engagement.attackers()) {
                SubjectId attackerId = attacker.actorId();
                ActorLocation location = actorLocations.get(attackerId);
                if (!hiveBioforms.contains(attackerId) || location == null || active && location.condition().status() != ActorLifeStatus.ALIVE) {
                    throw new IllegalArgumentException("active route engagement attacker must be one living hive bioform");
                }
                if (engagement.status() != RouteEngagementStatus.HOT && !location.position().equals(attacker.position())) {
                    throw new IllegalArgumentException("COLD engagement attacker must retain its exact route position");
                }
                if (active && !activeAttackers.add(attackerId)) throw new IllegalArgumentException("bioform cannot join multiple active route engagements");
            }
        }
    }
}
