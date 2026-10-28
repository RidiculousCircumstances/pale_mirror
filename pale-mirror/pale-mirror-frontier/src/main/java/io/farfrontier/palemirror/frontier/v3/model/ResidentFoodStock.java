package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Conservative food-owner absence fact, never source selection or meal permission.
 * All declared storage and all actor accounts are a superset of actual meal
 * sources. Positive stock, even at a blocked source, requires ordinary admission. */
final class ResidentFoodStock {
    private static final ImmutableKeyedInputView<SubjectId, Boolean> WAREHOUSES =
            new ImmutableKeyedInputView<>(SimulationCapacity.MAX_SETTLEMENTS);
    static boolean absent(FrontierWorldState state, SubjectId actor) {
        var resident = state.humanPopulation().resident(actor);
        if (resident == null) return false;
        var ledger = state.inventory().fungibleResources();
        var owner = resident.settlementId();
        if (!WAREHOUSES.get(state.bootstrap(), owner, List.of(state.inventory().containers(),
                ledger.accounts(), ledger.lots(), ledger.claims()), () -> absentInWarehouses(state, owner))) return false;
        for (var account : FungibleResourceReadIndex.actorAccounts(ledger, actor))
            if (hasFood(state, account, owner)) return false;
        return true;
    }
    private static boolean absentInWarehouses(FrontierWorldState state, SubjectId owner) {
        var ledger = state.inventory().fungibleResources();
        for (var id : ResourceStorageSelection.declaredContainers(state.inventory(), owner, ResidentMealOpportunity.FOOD_STORAGE)) {
            var account = FungibleResourceReadIndex.container(ledger, id).orElse(null);
            if (account != null && hasFood(state, account, owner)) return false;
        }
        return true;
    }
    private static boolean hasFood(FrontierWorldState state, CustodyAccount account, SubjectId owner) {
        for (var food : state.bootstrap().ruleset().residentLife().foods().foods().values())
            if (state.inventory().fungibleResources().unclaimedQuantity(account.id(), owner, food.itemKind()) > 0) return true;
        return false;
    }
    private ResidentFoodStock() { }
}
