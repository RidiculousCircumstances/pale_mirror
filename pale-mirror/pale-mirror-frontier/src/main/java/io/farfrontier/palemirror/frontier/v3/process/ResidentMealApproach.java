package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Optional;

/** The meal owns pickup readiness; movement and physical custody retain their own authority. */
final class ResidentMealApproach {
    private ResidentMealApproach() { }

    static boolean displaced(FrontierWorldState state, ResidentMeal meal) {
        var actor = state.actorLocations().get(meal.residentId());
        return meal.phase() == ResidentMeal.Phase.TAKE && actor != null
                && !actor.supportingSurface().equals(ResidentMealProcess.serviceSurface(state, meal));
    }

    static Optional<ResidentMealApproachRequired> plan(FrontierWorldState state, ResidentMeal meal) {
        var actor = state.actorLocations().get(meal.residentId());
        if (!displaced(state, meal) || actor.condition().status() != ActorLifeStatus.ALIVE
                || meal.pendingPhysicalStep().isPresent() || meal.coldTravel().isPresent()
                || !ActorExecutionCoordinator.coldAvailable(state, meal.residentId())) return Optional.empty();
        state.actorExecutions().requireCurrent(meal.executionId());
        return Optional.of(new ResidentMealApproachRequired(meal.executionId(), actor.body()));
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ResidentMealApproachRequired event) {
        var meal = state.humanPopulation().meals().get(subject);
        if (!subject.equals(event.execution().actorId()) || meal == null
                || !plan(state, meal).equals(Optional.of(event)))
            throw new IllegalArgumentException("meal reapproach lacks exact displaced COLD pickup and execution");
        return state.withHumanPopulation(state.humanPopulation().advanceMeal(meal, meal.reapproach()));
    }
}
