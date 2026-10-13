package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** One meal owner settles source loss both for stock withdrawal and physical custody handoff. */
final class ResidentMealForfeitureOwner implements FungibleClaimForfeitureOwner {
    @Override public void validate(FrontierWorldState state, ClaimAllocation claim, SubjectId source) {
        var meal = state.humanPopulation().meals().get(claim.claimantId());
        if (claim.purpose() != ClaimPurpose.RESIDENT_MEAL || meal == null || !meal.claimId().equals(claim.id())
                || !meal.sourceAccountId().equals(source) || !meal.portion().lotQuantities().equals(claim.lotQuantities())
                || state.inventory().fungibleResources().accounts().containsKey(meal.actorAccountId())
                || meal.pendingPhysicalStep().isPresent()
                || meal.phase() != ResidentMeal.Phase.MOVE && meal.phase() != ResidentMeal.Phase.TAKE)
            throw new IllegalArgumentException("meal source loss cannot retire a foreign or physically pending portion");
    }
    @Override public FungibleForfeitureSettlement settle(FrontierWorldState state, List<ClaimAllocation> claims,
                                                        FungibleForfeitureSettlement transaction) {
        for (var claim : claims) {
            var meal = state.humanPopulation().meals().get(claim.claimantId());
            var movements = new LinkedHashMap<>(transaction.movements());
            var movement = movements.get(meal.residentId());
            if (movement != null) {
                if (!movement.executionId().equals(meal.executionId()))
                    throw new IllegalArgumentException("meal source loss cannot retire foreign movement authority");
                movements.remove(meal.residentId());
            }
            transaction = transaction.withShipments(transaction.shipments(), transaction.executions().finish(meal.executionId()),
                    movements).withPopulation(transaction.population().abandonMealSource(meal));
        }
        return transaction;
    }
    @Override public List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before, ClaimAllocation claim) {
        var meal = before.humanPopulation().meals().get(claim.claimantId());
        return List.of(new SourceStockClaimLossOwner.ReleasedActivity(meal.residentId(), Optional.of(meal.progressScheduleId()),
                OptionalLong.of(Math.max(1L, meal.startedAtTick()))));
    }
}
