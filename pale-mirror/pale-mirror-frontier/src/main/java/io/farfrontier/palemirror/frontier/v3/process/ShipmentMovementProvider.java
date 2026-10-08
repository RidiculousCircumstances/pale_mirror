package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;

/** Shipment owns its semantic leg; shared geometry and movement own pathfinding and time. */
public final class ShipmentMovementProvider implements ActorMovementProvider {
    @Override public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.SHIPMENT; }
    @Override public void validate(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.ShipmentLeg leg))
            throw new IllegalArgumentException("shipment movement has a foreign context declaration");
        Shipment shipment = state.shipments().shipments().get(leg.shipmentId());
        if (shipment == null || shipment.terminal() || shipment.revision() != leg.shipmentRevision()
                || !shipment.execution().equals(movement.executionId()) || !shipment.movementOrder().equals(movement.order()))
            throw new IllegalArgumentException("shipment movement lacks its exact current leg");
        state.actorExecutions().requireCurrent(shipment.execution());
        ShipmentEndpointComposition.validate(state, shipment.sender());
        ShipmentEndpointComposition.validate(state, shipment.receiver());
    }
    @Override public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        validate(state, movement);
        return state.withChanges(update); // Same retained COURIER generation; movement is not a new job.
    }
    @Override public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        validate(state, movement);
        Shipment shipment = state.shipments().shipments().get(movement.order().ownerId());
        var knowledge = KnownPedestrianRouteKnowledge.forJourney(state,
                List.of(passage(state, shipment.sender()), passage(state, shipment.receiver())));
        return knowledge.plannedPath(start, movement.order());
    }
    @Override public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        validate(state, movement);
        Shipment shipment = state.shipments().shipments().get(movement.order().ownerId());
        KnownPedestrianRouteKnowledge.forJourney(state,
                List.of(passage(state, shipment.sender()), passage(state, shipment.receiver()))).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("shipment segment is neither a bounded prefix nor its declared goal");
    }
    @Override public List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        validate(state, movement);
        var access = serviceApproach(state, movement).orElseThrow();
        int end = Math.min(route.size(), TimedKnownRoute.MAX_SURFACES);
        if (!ServiceAccessCoordinator.available(state, access)) {
            var boundary = ServiceAccessCoordinator.boundary(state, access.pointId());
            for (int index = 1; index < end; index++) {
                if (boundary.occupied(route.get(index).standingBody())) { end = index; break; }
            }
        }
        return List.copyOf(route.subList(0, end));
    }
    @Override public java.util.Optional<ServiceAccessDemand.Identity> serviceApproach(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement);
        var shipment = state.shipments().shipments().get(movement.order().ownerId());
        return java.util.Optional.of(ShipmentServiceAccess.identity(shipment));
    }
    @Override public void requireColdRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route, long tick) {
        validate(state, movement);
        var shipment = state.shipments().shipments().get(movement.order().ownerId());
        KnownPedestrianRouteKnowledge.forJourney(state,
                List.of(passage(state, shipment.sender()), passage(state, shipment.receiver()))).requireRoute(route);
        var access = serviceApproach(state, movement).orElseThrow();
        boolean waitingOutside = !ServiceAccessCoordinator.available(state, access)
                && ServiceAccessCoordinator.boundary(state, access.pointId()).cleared(route.getLast().standingBody())
                && ServiceAccessCoordinator.boundary(state, access.pointId()).allowsWaitingRoute(route);
        if (route.size() > TimedKnownRoute.MAX_SURFACES || !waitingOutside
                && !movement.order().arrivedAt(route.getLast()) && route.size() != TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("shipment COLD segment lacks its bounded goal or service-wait boundary");
    }
    private static KnownPedestrianRouteKnowledge.SettlementPassage passage(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), endpoint.settlementId());
        var facility = settlement.structures().stream().filter(value -> value.id().equals(endpoint.facilityId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("shipment lost its declared facility passage"));
        return new KnownPedestrianRouteKnowledge.SettlementPassage(endpoint.settlementId(),
                new KnownPedestrianRouteKnowledge.Passage(facility, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS));
    }
    @Override public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement);
        return state.actorExecutions(); // Arrival does not unload, sell or release the shipment.
    }
    @Override public java.util.Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long atTick) {
        validate(state, movement);
        var shipment = state.shipments().shipments().get(movement.order().ownerId());
        if (shipment.pendingPhysicalStep().isPresent()) return java.util.Optional.empty();
        var current = ActorMovementProcess.bodyAt(state, movement.order().actorId(), atTick);
        return ServiceAccessCoordinator.boundary(state, shipment.sender().containerId()).cleared(current)
                && ServiceAccessCoordinator.boundary(state, shipment.receiver().containerId()).cleared(current)
                ? java.util.Optional.of(current) : java.util.Optional.empty();
    }
    @Override public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement); return state.actorExecutions(); // Suspend the shipment later; never retire its cargo owner as a journey.
    }
    @Override public boolean permitsReplacement(ResidentActivityChoice.Kind next) { return next == ResidentActivityChoice.Kind.EAT; }
    @Override public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long atTick) {
        return List.of(ShipmentProcess.wake(movement.order().ownerId(), atTick));
    }
    @Override public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long atTick) {
        return List.of(ShipmentProcess.wake(movement.order().ownerId(), atTick));
    }
}
