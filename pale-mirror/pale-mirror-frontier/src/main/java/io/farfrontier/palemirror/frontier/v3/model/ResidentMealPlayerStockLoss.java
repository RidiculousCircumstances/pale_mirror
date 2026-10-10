package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Food ownership decides whether its still-unconsumed source can be withdrawn. */
final class ResidentMealPlayerStockLoss implements PlayerStockClaimLossOwner {
    @Override public ClaimPurpose purpose() { return ClaimPurpose.RESIDENT_MEAL; }
    @Override public void validate(FrontierWorldState state, SubjectId source, ClaimAllocation claim) {
        var meal = state.humanPopulation().meals().get(claim.claimantId());
        if (claim.purpose() != purpose() || meal == null || !meal.claimId().equals(claim.id())
                || !meal.sourceAccountId().equals(source) || !meal.portion().lotQuantities().equals(claim.lotQuantities())
                || state.inventory().fungibleResources().accounts().containsKey(meal.actorAccountId())
                || meal.pendingPhysicalStep().isPresent()
                || meal.phase() != ResidentMeal.Phase.MOVE && meal.phase() != ResidentMeal.Phase.TAKE)
            throw new IllegalArgumentException("meal source loss cannot retire a foreign or physically pending portion");
    }
    @Override public Settlement settle(FrontierWorldState state, ClaimAllocation claim, Settlement transaction) {
        var meal = state.humanPopulation().meals().get(claim.claimantId());
        var resources = transaction.resources();
        return new Settlement(resources.withShipments(resources.shipments(), resources.executions().finish(meal.executionId()),
                resources.movements()), transaction.population().abandonMealSource(meal));
    }
    @Override public List<ReleasedActivity> released(FrontierWorldState before, ClaimAllocation claim) {
        var meal = before.humanPopulation().meals().get(claim.claimantId());
        return List.of(new ReleasedActivity(meal.residentId(), Optional.of(meal.progressScheduleId()),
                OptionalLong.of(Math.max(1L, meal.startedAtTick()))));
    }
}
