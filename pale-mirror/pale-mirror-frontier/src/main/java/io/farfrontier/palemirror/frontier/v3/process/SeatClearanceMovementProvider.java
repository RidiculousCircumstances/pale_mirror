package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Furniture access yields through the common SERVICE_EXIT execution, with no resource turn. */
public final class SeatClearanceMovementProvider implements ActorMovementProvider {
    public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.SEAT_CLEARANCE; }
    public void validate(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.SeatClearance exit)
                || movement.executionId().activityKind() != ActorActivityKind.SERVICE_EXIT
                || !movement.order().ownerId().equals(movement.order().actorId())
                || movement.order().capability() != TraversalCapability.PEDESTRIAN
                || state.humanPopulation().resident(movement.order().actorId()) == null)
            throw new IllegalArgumentException("foreign seat clearance movement");
        SeatAccessClearance.place(state.bootstrap(), exit.home(), exit.seat());
    }
    public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        validate(state, movement);
        if (SeatAccessClearance.requestedAt(state, movement.order().actorId()).filter(place -> {
            var exit = (ActorMovementContext.SeatClearance) movement.context();
            return place.home().equals(exit.home()) && place.id().equals(exit.seat());
        }).isEmpty()) throw new IllegalArgumentException("seat clearance has no actual obstructed reservation");
        return ActorExecutionComposition.LIFECYCLE.prepareBegin(state, movement.executionId(), movement.issuedAtTick()).commit(state, update);
    }
    public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement);
        return ServiceClearanceRoutes.from(state, movement.order().actorId(), state.actorLocations().get(movement.order().actorId()).supportingSurface()).knowledge();
    }
    public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        validate(state, movement);
        var knowledge = ServiceClearanceRoutes.from(state, movement.order().actorId(), start).knowledge();
        if (ActorExecutionCoordinator.coldAvailable(state, movement.order().actorId())
                && movement.order().legalStations().stream().allMatch(ServiceDestinationClaims.forImmediateExit(state, movement.order().actorId())::contains))
            throw new KnownPedestrianNavigation.RouteUnavailable("seat clearance destination is occupied");
        return knowledge.plannedPath(start, movement.order());
    }
    public List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        validate(state, movement); return route;
    }
    public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        validate(state, movement);
        if (route.isEmpty() || !movement.order().arrivedAt(route.getLast())) throw new IllegalArgumentException("seat clearance did not reach its retained goal");
        var clearance = ServiceClearanceRoutes.from(state, movement.order().actorId(), route.getFirst());
        clearance.knowledge().requireRoute(route);
        if (!clearance.allowsLocal(route)) throw new IllegalArgumentException("seat clearance entered a demanded service throat");
        var exit = (ActorMovementContext.SeatClearance) movement.context();
        var seat = SeatAccessClearance.place(state.bootstrap(), exit.home(), exit.seat());
        if (route.getLast().equals(seat.approach()) || route.getLast().equals(seat.egress())
                || SeatAccessClearance.furnitureContact(state, route.getLast())
                || !SeatAccessClearance.parkingFloor(state, seat, route.getLast()))
            throw new IllegalArgumentException("seat clearance still occupies its access contact");
    }
    public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement); return state.actorExecutions().finish(movement.executionId());
    }
    public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long tick) {
        validate(state, movement); return Optional.of(ActorMovementProcess.bodyAt(state, movement.order().actorId(), tick));
    }
    public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) { return arrivalAuthority(state, movement); }
    public boolean permitsReplacement(ResidentActivityChoice.Kind next) { return next == ResidentActivityChoice.Kind.WORK || next == ResidentActivityChoice.Kind.EAT; }
    public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long tick) {
        return List.of(ResidentActivityProcess.wakeAfterActivity(movement.order().actorId(), tick));
    }
    public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long tick) { return arrived(state, movement, tick); }
}
