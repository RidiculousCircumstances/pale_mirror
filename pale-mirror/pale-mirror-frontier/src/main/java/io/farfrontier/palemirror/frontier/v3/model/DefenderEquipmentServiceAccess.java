package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.List;
import java.util.Set;

/** Exact selected return publishes a real approach; atomic issue/return keeps shared contact rules. */
final class DefenderEquipmentServiceAccess implements ServiceAccessCapability {
    private final EquipmentTransferServiceAccess transfers = new EquipmentTransferServiceAccess(ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT);
    @Override public ServiceAccessDemand.Kind kind() { return ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT; }
    @Override public ServiceAccessDemand.Priority priority() { return ServiceAccessDemand.Priority.WORK; }
    @Override public List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId point) {
        var result = new java.util.ArrayList<>(transfers.demands(state, point));
        var identities = new java.util.HashSet<ServiceAccessDemand.Identity>(); result.forEach(demand -> identities.add(demand.identity()));
        for (var parent : state.strategicPlans().settlementAssaults().values()) {
            var visit = parent.equipmentReturnVisit().orElse(null);
            if (visit == null || visit.execution().isEmpty() || !visit.access().containerId().equals(point)
                    || !state.actorExecutions().owns(visit.actorId(), ActorActivityKind.EQUIPMENT_SERVICE, visit.ownerId())
                    || identities.contains(visit.request().identity())) continue;
            if (!ReferenceContainerCustody.isReferenceContainer(state, point) || ReferenceContainerCustody.blocksCanonicalUse(state, point)) continue;
            boolean occupied = ServiceAccessCoordinator.occupies(state, visit.access().accessBoundary(), visit.actorId());
            var move = state.actorMovements().get(visit.actorId());
            boolean approaching = visit.phase() == DefenderEquipmentReturnVisit.Phase.APPROACH && move != null
                    && visit.execution().equals(java.util.Optional.of(move.executionId()));
            if (!occupied && !approaching) continue;
            result.add(new ServiceAccessDemand(visit.request().identity(), priority(),
                    occupied ? ServiceAccessDemand.Presence.OCCUPIED : ServiceAccessDemand.Presence.APPROACH,
                    visit.request().requestedAtTick(), false));
            identities.add(visit.request().identity());
        }
        return List.copyOf(result);
    }
    @Override public Set<SubjectId> wakePoints(FrontierWorldState previous, FrontierWorldState next) {
        var points = new java.util.HashSet<>(transfers.wakePoints(previous, next));
        if (previous.strategicPlans() != next.strategicPlans() || previous.actorExecutions() != next.actorExecutions()
                || previous.actorMovements() != next.actorMovements() || previous.actorLocations() != next.actorLocations()) {
            java.util.stream.Stream.concat(previous.strategicPlans().settlementAssaults().values().stream(), next.strategicPlans().settlementAssaults().values().stream())
                    .flatMap(parent -> parent.equipmentReturnVisit().stream()).forEach(visit -> points.add(visit.access().containerId()));
        }
        return Set.copyOf(points);
    }
}
