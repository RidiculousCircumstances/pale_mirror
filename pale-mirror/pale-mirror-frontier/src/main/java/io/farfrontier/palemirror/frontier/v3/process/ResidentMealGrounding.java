package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianVerticalSettlement;
import java.util.Optional;

/** Meal-owned safe checkpoint; common geometry owns gravity and landing proof. */
final class ResidentMealGrounding {
    private ResidentMealGrounding() { }
    static Optional<ResidentMealGrounded> plan(FrontierWorldState state, ResidentMeal meal, long tick) {
        var actor = state.actorLocations().get(meal.residentId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || tick < meal.startedAtTick()
                || meal.portable() || meal.pendingPhysicalStep().isPresent() || meal.coldTravel().isPresent()
                || meal.phase() != ResidentMeal.Phase.MOVE && meal.phase() != ResidentMeal.Phase.CLEAR_ACCESS
                || !ActorExecutionCoordinator.coldAvailable(state, meal.residentId())) return Optional.empty();
        state.actorExecutions().requireCurrent(meal.executionId());
        var geometry = ResidentMealKnownNavigation.departureKnowledge(state, meal, actor.supportingSurface()).geometry();
        return PedestrianVerticalSettlement.confirmed(actor.body(), geometry, actor.navigationProfile())
                .map(landing -> new ResidentMealGrounded(meal.executionId(), tick, actor.body(), landing));
    }
    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ResidentMealGrounded event) {
        var meal = state.humanPopulation().meals().get(subject);
        if (!subject.equals(event.execution().actorId()) || meal == null
                || !meal.executionId().equals(event.execution()) || !plan(state, meal, event.atTick()).equals(Optional.of(event)))
            throw new IllegalArgumentException("meal grounding lacks exact COLD authority, pose or clear landing");
        return state.withActorBody(subject, event.landing().standingBody());
    }
}
