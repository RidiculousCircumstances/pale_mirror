package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Read-only feasible meal source; hunger alone does not make a meal an executable activity. */
public final class ResidentMealOpportunity {
    private ResidentMealOpportunity() { }

    public record Source(SubjectId depotId, SurfaceAnchor clearingSurface,
                         FungibleResourceCustodySupport.LotSelection selection, FoodPortion portion) {
        public Source {
            Objects.requireNonNull(depotId, "meal depot");
            Objects.requireNonNull(clearingSurface, "meal clearing surface");
            Objects.requireNonNull(selection, "meal resource selection");
            Objects.requireNonNull(portion, "meal food portion");
            if (!selection.lotQuantities().equals(portion.lotQuantities()))
                throw new IllegalArgumentException("meal opportunity differs from its exact portion");
        }
    }

    public static Optional<Source> find(FrontierWorldState state, SubjectId residentId) {
        return find(state, residentId, state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
    }

    public static Optional<Source> find(FrontierWorldState state, SubjectId residentId, long atTick) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null || state.humanPopulation().meals().containsKey(residentId)
                || state.actorMovements().containsKey(residentId)
                || state.humanPopulation().migration(residentId) != null) return Optional.empty();
        ActorLocation body = state.actorLocations().get(residentId);
        if (body == null || body.condition().status() != ActorLifeStatus.ALIVE) return Optional.empty();
        SubjectId depot = FrontierWorldState.depotId(resident.settlementId());
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return Optional.empty();
        if (!ServiceAccessCoordinator.depotMayStartMeal(state, depot, residentId)) return Optional.empty();
        SurfaceAnchor clearing = ServiceAccessCoordinator.mealClearingSurface(state, residentId).orElse(null);
        var account = FungibleResourceCustodySupport.accountAtContainer(state, depot).orElse(null);
        if (clearing == null || account == null) return Optional.empty();
        var rules = state.bootstrap().ruleset().residentLife();
        int wanted = state.humanPopulation().nutrition(residentId).accrueThrough(atTick, rules,
                resident.characteristics().effectiveMetabolismPermille(atTick)).nutritionWanted(rules);
        // Stable item-kind ordering is selection policy, never implicit resource identity.
        for (var food : new java.util.TreeMap<>(rules.foods().foods()).values()) {
            int available = state.inventory().fungibleResources().unclaimedQuantity(account.id(),
                    resident.settlementId(), food.itemKind());
            int quantity = food.portionFor(wanted, available);
            if (quantity == 0) continue;
            var selection = FungibleResourceCustodySupport.selectAtContainer(state, depot,
                    resident.settlementId(), food.itemKind(), quantity).orElseThrow();
            return Optional.of(new Source(depot, clearing, selection,
                    new FoodPortion(food.itemKind(), food.nutritionPerItem(), selection.lotQuantities())));
        }
        return Optional.empty();
    }
}
