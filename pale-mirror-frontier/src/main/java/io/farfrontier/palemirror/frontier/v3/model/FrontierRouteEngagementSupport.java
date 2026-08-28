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
            for (SubjectId attackerId : engagement.attackerIds()) {
                ActorLocation location = actorLocations.get(attackerId);
                if (!hiveBioforms.contains(attackerId) || location == null || location.condition().status() != ActorLifeStatus.ALIVE) {
                    throw new IllegalArgumentException("route engagement attacker must be one living hive bioform");
                }
                if (active && !activeAttackers.add(attackerId)) throw new IllegalArgumentException("bioform cannot join multiple active route engagements");
            }
        }
    }
}
