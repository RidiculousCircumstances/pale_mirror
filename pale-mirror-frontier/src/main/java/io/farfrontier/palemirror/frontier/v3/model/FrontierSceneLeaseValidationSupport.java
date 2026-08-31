package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Aggregate validation shared by all typed scene causes. */
final class FrontierSceneLeaseValidationSupport {
    private FrontierSceneLeaseValidationSupport() { }

    static void validate(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actors, Map<SubjectId, StructureCondition> structures,
                         Map<SubjectId, RouteOperation> operations, StrategicPlanState plans, Map<SceneLeaseId, SceneLease> leases,
                         Set<SubjectId> activeAmbientActors) {
        Set<SubjectId> leasedActors = new HashSet<>(), leasedOperations = new HashSet<>();
        for (Map.Entry<SceneLeaseId, SceneLease> entry : leases.entrySet()) {
            SceneLease lease = entry.getValue();
            if (!entry.getKey().equals(lease.id()) || !bootstrap.worldId().equals(lease.worldId())) {
                throw new IllegalArgumentException("scene lease identity or world is invalid");
            }
            Set<SubjectId> expected = expectedMembers(bootstrap, actors, structures, operations, plans, lease, leasedOperations);
            Set<SubjectId> members = lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet());
            if (!members.equals(expected)) throw new IllegalArgumentException("scene lease members must exactly match its canonical scene actors");
            for (SubjectId actor : members) {
                if (lease.status() != SceneLeaseStatus.CLOSED && !leasedActors.add(actor)) {
                    throw new IllegalArgumentException("actor cannot belong to multiple active scene leases");
                }
                if (lease.status() != SceneLeaseStatus.CLOSED && activeAmbientActors.contains(actor)) {
                    throw new IllegalArgumentException("actor cannot have both scene and ambient execution leases");
                }
            }
        }
    }

    private static Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, Map<SubjectId, ActorLocation> actors,
                                                   Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                   StrategicPlanState plans, SceneLease lease, Set<SubjectId> leasedOperations) {
        if (lease.cause() instanceof SettlementAssaultSceneCause cause) {
            SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(plans, cause);
            validateAssaultLifecycle(bootstrap, structures, lease, assault);
            Set<SubjectId> values = new HashSet<>(assault.attackerIds()); values.addAll(assault.defenderIds());
            return Set.copyOf(values);
        }
        LogisticsSceneCause cause = lease.logisticsCause();
        RouteOperation operation = operations.get(cause.operationId());
        if (operation == null || !operation.cargoId().equals(cause.cargoId())) {
            throw new IllegalArgumentException("scene lease must bind its current en-route operation state");
        }
        boolean enRoute = operation.stage() == OperationStage.EN_ROUTE && operation.currentPosition().equals(lease.handoffPosition())
                && operation.activeTravel().map(travel -> travel.cargoAnchor().equals(cause.cargoPosition())).orElse(true);
        boolean interrupted = operation.stage() == OperationStage.INTERRUPTED
                && (lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        boolean unresolved = operation.stage() == OperationStage.FAILED && lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART
                && lease.recoveryEvidence().isPresent();
        if (lease.status() != SceneLeaseStatus.CLOSED && !enRoute && !interrupted && !unresolved) {
            throw new IllegalArgumentException("active scene lease must bind its current en-route operation state");
        }
        if (lease.status() != SceneLeaseStatus.CLOSED && !leasedOperations.add(cause.operationId())) {
            throw new IllegalArgumentException("operation cannot have multiple active scene leases");
        }
        if (cause.engagementId().isEmpty()) return Set.copyOf(operation.participantIds());
        RouteEngagement engagement = plans.routeEngagements().get(cause.engagementId().orElseThrow());
        boolean aborted = operation.stage() == OperationStage.INTERRUPTED
                && (lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART)
                && engagement != null && engagement.status() == RouteEngagementStatus.RESOLVED
                && engagement.outcome().filter(value -> value == RouteEngagementOutcome.ABORTED).isPresent();
        if (engagement == null || !engagement.operationId().equals(operation.id()) || !cause.cargoPosition().equals(engagement.intercept())
                || lease.status() != SceneLeaseStatus.CLOSED && engagement.status() != RouteEngagementStatus.COLD_COMBAT
                && engagement.status() != RouteEngagementStatus.HOT && engagement.status() != RouteEngagementStatus.UNKNOWN_AFTER_RESTART
                && engagement.status() != RouteEngagementStatus.CONFLICT && !aborted) {
            throw new IllegalArgumentException("scene lease must bind one active canonical engagement");
        }
        Set<SubjectId> values = new HashSet<>(operation.participantIds()); values.addAll(engagement.attackerIds());
        return Set.copyOf(values);
    }

    private static void validateAssaultLifecycle(FrontierBootstrap bootstrap, Map<SubjectId, StructureCondition> structures, SceneLease lease, SettlementAssault assault) {
        if (!FrontierSettlementAssaultSceneSupport.targetIntact(bootstrap, structures, assault) && lease.status() != SceneLeaseStatus.CLOSED) {
            throw new IllegalArgumentException("active assault scene target geometry is destroyed");
        }
        boolean valid = switch (lease.status()) {
            case PREPARED -> assault.status() == SettlementAssaultStatus.COLD_COMBAT;
            case HOT, DRAINING -> assault.status() == SettlementAssaultStatus.HOT;
            case UNKNOWN_AFTER_RESTART -> assault.status() == SettlementAssaultStatus.UNKNOWN_AFTER_RESTART;
            case CONFLICT -> assault.status() == SettlementAssaultStatus.CONFLICT;
            case CLOSED -> assault.status() == SettlementAssaultStatus.COLD_COMBAT || assault.status() == SettlementAssaultStatus.RESOLVED;
        };
        if (!valid) throw new IllegalArgumentException("assault scene lease and canonical lifecycle disagree");
    }
}
