package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Activity admission for optional service-buffer turnover; no food effect or separate movement authority. */
final class ResidentServiceTurnover {
    private ResidentServiceTurnover() { }
    static Optional<ActorMovement> select(FrontierWorldState state, SubjectId actorId, long atTick) {
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        ActorLocation actor = state.actorLocations().get(actorId);
        if (resident == null || actor == null) return Optional.empty();
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(actorId);
        boolean idle = ResidentActivityCoordinator.assess(state, actorId, atTick).kind() == ResidentActivityChoice.Kind.IDLE;
        if (resident == null || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || state.actorMovements().containsKey(actorId) || state.humanPopulation().meals().containsKey(actorId)
                || ActorExecutionCoordinator.sceneOwns(state, actorId)
                || !ResidentWorkYield.assess(state, assignment).ready()
                || !idle && !ActivityExecutionCapabilities.waitingForServiceResource(state, assignment))
            return Optional.empty();
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED && lease.status() != AmbientLeaseStatus.HOT)
            return Optional.empty();
        Optional<ServiceAccessPoint> occupied = ServiceAccessCoordinator.turnoverPoint(state, actorId);
        if (occupied.isEmpty()) return Optional.empty();
        ServiceAccessPoint point = occupied.orElseThrow();
        List<ServiceAccessPoint> points = SettlementServiceAccessPoints.forSettlement(state, point.settlementId());
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), point.settlementId());
        SettlementStructure facility = settlement.structures().stream()
                .filter(value -> value.id().equals(point.facilityId())).findFirst().orElseThrow();
        var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), List.of(
                new KnownPedestrianRouteKnowledge.Passage(facility, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
        var excluded = new HashSet<SurfaceAnchor>();
        state.actorLocations().entrySet().stream().filter(entry -> !entry.getKey().equals(actorId))
                .filter(entry -> entry.getValue().condition().status() == ActorLifeStatus.ALIVE)
                .map(entry -> entry.getValue().supportingSurface()).forEach(excluded::add);
        state.humanPopulation().meals().values().stream().map(ResidentMeal::clearingSurface).forEach(excluded::add);
        state.actorMovements().values().stream().flatMap(value -> value.order().legalStations().stream()).forEach(excluded::add);
        return ServiceAreaDestinations.select(points, actorId, actor.supportingSurface(), knowledge, excluded)
                .map(destination -> new ActorMovement(new MovementOrder(actorId, actorId, 0L,
                        Math.addExact(atTick, 1L), List.of(destination), TraversalCapability.PEDESTRIAN,
                        MovementOrder.ArrivalPolicy.EXACT_STATION), atTick,
                        new ActorMovementContext.ServiceExit(point.settlementId(),
                                FrontierWorldState.depotId(point.settlementId())),
                        state.actorExecutions().next(actorId, ActorActivityKind.SERVICE_EXIT, actorId)));
    }
}
