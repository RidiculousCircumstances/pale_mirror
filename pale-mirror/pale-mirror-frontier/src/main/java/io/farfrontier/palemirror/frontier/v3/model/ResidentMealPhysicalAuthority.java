package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact depot authority while a prepared resident take may have changed the loaded chest. */
public final class ResidentMealPhysicalAuthority {
    private ResidentMealPhysicalAuthority() { }

    public static boolean pendingForContainer(FrontierWorldState state, SubjectId containerId) {
        return state.humanPopulation().meals().values().stream().anyMatch(meal ->
                meal.depotId().equals(containerId) && meal.phase() == ResidentMeal.Phase.TAKE
                        && meal.pendingPhysicalStep().isPresent());
    }
}
