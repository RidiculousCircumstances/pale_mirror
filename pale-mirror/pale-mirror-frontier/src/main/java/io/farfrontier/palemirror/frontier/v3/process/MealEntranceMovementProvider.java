package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
/** Meal purpose over the sole shared journey/navigation owner. No food effect, queue timer or second mover. */
public final class MealEntranceMovementProvider implements ActorMovementProvider {
    public ActorMovementContext.Provider key() { return ActorMovementContext.Provider.MEAL_ENTRANCE; }
    private static ResidentMeal meal(FrontierWorldState state, ActorMovement movement) {
        var meal = state.humanPopulation().meals().get(movement.order().actorId());
        if (meal == null) throw new IllegalArgumentException("meal entrance lost its exact owner");
        return meal;
    }
    public static ActorMovement requested(FrontierWorldState state, ResidentMealEntranceRequested request, long tick) {
        ActorBodyAuthority.requireActuation(state, request.actor());
        var actor = request.actor().body().actorId(); var meal = state.humanPopulation().meals().get(actor);
        if (meal == null) throw new IllegalArgumentException("meal entrance has no retained meal");
        if (!meal.executionId().equals(request.actor().execution()) || meal.portable() || meal.phase() != ResidentMeal.Phase.MOVE
                || meal.pendingPhysicalStep().isPresent() || meal.coldTravel().isPresent() || state.actorMovements().containsKey(actor)
                || !ResidentMealServiceAccess.available(state, meal.depotId(), actor) || state.combat().evasions().containsKey(actor)
                || ActorInventoryInteractionFences.pending(state, actor)) throw new IllegalArgumentException("meal entrance is not currently admitted");
        var service = ResourceStorageAccess.at(state, meal.depotId()).interactionStation(state);
        if (state.actorLocations().get(actor).supportingSurface().equals(service))
            throw new IllegalArgumentException("meal already reached its source; the food owner must observe arrival");
        var order = new MovementOrder(meal.claimId(), actor, FrontierWireTags.tag(meal.phase()), meal.startedAtTick() + 1L,
                List.of(service), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        return new ActorMovement(order, tick, new ActorMovementContext.MealEntrance(meal.depotId()), meal.executionId());
    }
    public void validate(FrontierWorldState state, ActorMovement movement) {
        if (!(movement.context() instanceof ActorMovementContext.MealEntrance context)) throw new IllegalArgumentException("foreign meal entrance context");
        var meal = meal(state, movement); state.actorExecutions().requireCurrent(meal.executionId());
        if (!meal.executionId().equals(movement.executionId()) || meal.phase() != ResidentMeal.Phase.MOVE || meal.portable()
                || !meal.depotId().equals(context.pointId()) || !movement.order().legalStations().equals(List.of(ResourceStorageAccess.at(state, meal.depotId()).interactionStation(state)))
                || !movement.order().ownerId().equals(meal.claimId()) || movement.order().goalRevision() != meal.startedAtTick() + 1L
                || movement.order().goalOrdinal() != FrontierWireTags.tag(meal.phase()) || movement.order().capability() != TraversalCapability.PEDESTRIAN
                || movement.order().arrivalPolicy() != MovementOrder.ArrivalPolicy.EXACT_STATION || meal.pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("invalid meal entrance declaration");
    }
    public FrontierWorldState start(FrontierWorldState state, ActorMovement movement, FrontierWorldStateUpdate update) {
        validate(state, movement);
        if (!ResidentMealServiceAccess.available(state, meal(state, movement).depotId(), movement.order().actorId()))
            throw new IllegalArgumentException("meal entrance turn changed before admission");
        return state.withChanges(update);
    }
    public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state, ActorMovement movement) {
        validate(state, movement); return ResidentMealKnownNavigation.departureKnowledge(state, meal(state, movement), state.actorLocations().get(movement.order().actorId()).supportingSurface());
    }
    public List<SurfaceAnchor> route(FrontierWorldState state, ActorMovement movement, SurfaceAnchor start) {
        validate(state, movement); return ResidentMealKnownNavigation.departureKnowledge(state, meal(state, movement), start).plannedPath(start, movement.order());
    }
    public void requireRoute(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) {
        validate(state, movement); ResidentMealKnownNavigation.departureKnowledge(state, meal(state, movement), route.getFirst()).requireRoute(route);
        if (!movement.order().arrivedAt(route.getLast())) throw new IllegalArgumentException("meal entrance route missed its exact source station");
    }
    public List<SurfaceAnchor> coldSegment(FrontierWorldState state, ActorMovement movement, List<SurfaceAnchor> route) { validate(state, movement); return route; }
    public ActorExecutionState arrivalAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state, ActorMovement movement, long tick) {
        validate(state, movement); return Optional.of(ActorMovementProcess.bodyAt(state, movement.order().actorId(), tick));
    }
    public ActorExecutionState interruptionAuthority(FrontierWorldState state, ActorMovement movement) { validate(state, movement); return state.actorExecutions(); }
    public boolean permitsReplacement(ResidentActivityChoice.Kind next) {
        return next == ResidentActivityChoice.Kind.SAFETY || next == ResidentActivityChoice.Kind.CARE || next == ResidentActivityChoice.Kind.EAT;
    }
    public List<ProposedEvent> arrived(FrontierWorldState state, ActorMovement movement, long tick) {
        var meal = meal(state, movement); var due = ResidentMealProcess.progress(meal, tick + 1);
        return List.of(new ProposedEvent(meal.residentId(), new ScheduleEffect.Rescheduled(due.id(), due)));
    }
    public List<ProposedEvent> interrupted(FrontierWorldState state, ActorMovement movement, long tick) { return List.of(); }
}
