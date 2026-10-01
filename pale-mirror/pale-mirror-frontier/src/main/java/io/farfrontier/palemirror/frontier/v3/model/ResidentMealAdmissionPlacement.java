package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Meal-owned admission capability: moving an unbegun take cannot count as service arrival. */
final class ResidentMealAdmissionPlacement {
    private ResidentMealAdmissionPlacement() { }
    static boolean mayRelocate(FrontierWorldState state, SubjectId actorId) {
        ResidentMeal meal = state.humanPopulation().meals().get(actorId);
        if (meal == null) throw new IllegalArgumentException("meal admission lost its exact activity");
        return meal.pendingPhysicalStep().isEmpty() && meal.phase() != ResidentMeal.Phase.CONSUME;
    }
    static FrontierWorldState confirmed(FrontierWorldState state, SubjectId actorId, BodyPosition body) {
        ResidentMeal meal = state.humanPopulation().meals().get(actorId);
        if (meal == null) throw new IllegalArgumentException("meal admission lost its exact activity");
        if (meal.phase() == ResidentMeal.Phase.TAKE
                && !body.equals(SettlementServiceAccessPoints.depotPort(state, meal.settlementId()).serviceSurface().standingBody()))
            return state.withHumanPopulation(state.humanPopulation().advanceMeal(meal, meal.reapproach()));
        return state;
    }
}
