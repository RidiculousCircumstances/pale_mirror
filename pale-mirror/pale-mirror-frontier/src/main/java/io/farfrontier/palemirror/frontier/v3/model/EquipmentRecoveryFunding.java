package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Minimum restart material envelope. Labour value is not cash and future
 * useful wear is not proof that the first replacement can be obtained. */
final class EquipmentRecoveryFunding {
    record Cost(long cash, boolean quoted) {
        boolean funded(long money) { return quoted && cash <= money; }
    }
    static Cost cost(FrontierWorldState state, SubjectId home, ProductionRecipe recipe,
            int operations, SourcingMarket market, long tick) {
        if (operations < 1) throw new IllegalArgumentException("empty equipment restart");
        var stations = AutomaticStationProduction.stations(state, home, recipe);
        if (stations.isEmpty()) return new Cost(Long.MAX_VALUE, false);
        long supplied = stations.stream().mapToLong(record -> ProductionStationEnergy.suppliedHeat(state,
                record.productionStation().orElseThrow().id())).max().orElse(0);
        long heat = Math.max(0, Math.multiplyExact(operations, recipe.heatPerBatch()) - supplied);
        Cost best = null;
        for (var ingredient : recipe.ingredient().acceptedItems().stream().sorted().toList()) {
            var needs = Map.of(ingredient, Math.multiplyExact((long) operations, recipe.ingredient().quantity()));
            if (heat == 0) best = better(best, assess(state, home, needs, market, tick), market.availableMoney());
            else for (var fuel : state.bootstrap().ruleset().production().fuels()) {
                var combined = new TreeMap<>(needs);
                combined.merge(fuel.itemKind(), Math.ceilDiv(heat, fuel.heatUnits()), Math::addExact);
                best = better(best, assess(state, home, combined, market, tick), market.availableMoney());
            }
        }
        return Objects.requireNonNull(best);
    }
    private static Cost assess(FrontierWorldState state, SubjectId home, Map<String, Long> needs, SourcingMarket market, long tick) {
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        long cash = 0; boolean quoted = true;
        for (var entry : needs.entrySet()) {
            long missing = Math.max(0, entry.getValue() - GoodsParticipantStorage.unclaimed(state, participant, entry.getKey()));
            var quote = SourcingPurchaseCost.quote(market, entry.getKey(), missing, Long.MAX_VALUE);
            cash = Math.addExact(cash, quote.cash()); quoted &= quote.quantity() == missing;
            if (quote.quantity() < missing) {
                long remaining = missing - quote.quantity();
                long expected = ConditionalSupplyPricing.best(state, participant, entry.getKey(), remaining,
                        market.availableMoney(), tick).map(ConditionalSupplyPricing.Estimate::cashUnitExpense)
                        .orElseGet(() -> GoodsResourceValuation.replacement(state, participant, entry.getKey(), tick).raw());
                cash = Math.addExact(cash, Math.multiplyExact(remaining, expected));
            }
        }
        return new Cost(cash, quoted);
    }
    private static Cost better(Cost old, Cost candidate, long money) {
        if (old == null || candidate.funded(money) && !old.funded(money)
                || candidate.funded(money) == old.funded(money) && candidate.cash() < old.cash()) return candidate;
        return old;
    }
    private EquipmentRecoveryFunding() { }
}
