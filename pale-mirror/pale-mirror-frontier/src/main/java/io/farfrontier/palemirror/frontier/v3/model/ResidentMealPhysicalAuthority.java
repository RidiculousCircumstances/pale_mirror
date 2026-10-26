package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Food-owner authority while a prepared effect may have changed its source or actor inventory. */
public final class ResidentMealPhysicalAuthority {
    private ResidentMealPhysicalAuthority() { }

    public static java.util.Optional<SubjectId> pendingOwnerForActor(FrontierWorldState state, SubjectId actor) {
        var meal = state.humanPopulation().meals().get(actor);
        return meal != null && meal.pendingPhysicalStep().isPresent()
                ? java.util.Optional.of(meal.claimId()) : java.util.Optional.empty();
    }

    public static boolean pendingForContainer(FrontierWorldState state, SubjectId containerId) {
        return state.humanPopulation().meals().values().stream().anyMatch(meal ->
                !meal.portable() && meal.depotId().equals(containerId) && meal.phase() == ResidentMeal.Phase.TAKE
                        && meal.pendingPhysicalStep().isPresent())
                || state.humanPopulation().mealResourceObligations().values().stream().anyMatch(obligation ->
                    !obligation.portable() && obligation.depotId().equals(containerId)
                        && obligation.custodyState() == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING);
    }
}
