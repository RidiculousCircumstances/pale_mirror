package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Food-owner authority while a prepared effect may have changed its source or actor inventory. */
public final class ResidentMealPhysicalAuthority {
    private ResidentMealPhysicalAuthority() { }
    private static final ImmutableInputView<java.util.Set<SubjectId>> PENDING = new ImmutableInputView<>();

    public static java.util.Optional<SubjectId> pendingOwnerForActor(FrontierWorldState state, SubjectId actor) {
        var meal = state.humanPopulation().meals().get(actor);
        return meal != null && meal.pendingPhysicalStep().isPresent()
                ? java.util.Optional.of(meal.claimId()) : java.util.Optional.empty();
    }

    public static boolean pendingForContainer(FrontierWorldState state, SubjectId containerId) {
        return PENDING.get(java.util.List.of(state.humanPopulation().meals(), state.humanPopulation().mealResourceObligations()), () -> {
            var containers = new java.util.HashSet<SubjectId>();
            for (var meal : state.humanPopulation().meals().values())
                if (!meal.portable() && meal.phase() == ResidentMeal.Phase.TAKE && meal.pendingPhysicalStep().isPresent())
                    containers.add(meal.depotId());
            for (var obligation : state.humanPopulation().mealResourceObligations().values())
                if (!obligation.portable() && obligation.custodyState() == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING)
                    containers.add(obligation.depotId());
            return java.util.Set.copyOf(containers);
        }).contains(containerId);
    }
}
