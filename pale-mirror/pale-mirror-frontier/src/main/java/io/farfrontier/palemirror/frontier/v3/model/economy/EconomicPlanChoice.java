package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;

/** Bounded portfolio comparison. Cash savings never justify less secured urgent food.
 * Conditional sales and outstanding receivables are not spendable balances. */
final class EconomicPlanChoice {
    static boolean prefer(SupplyPlanner.Plan candidate, SupplyPlanner.Plan current, EconomicSnapshot facts) {
        for (var entry : facts.stocks().entrySet()) {
            if (!entry.getValue().policy().essential()) continue;
            var next = candidate.require(entry.getKey()); var prior = current.require(entry.getKey());
            if (next.production() < prior.production() || next.urgentPurchase() > prior.urgentPurchase()) return false;
        }
        var next = candidate.economy().orElseThrow(); var prior = current.economy().orElseThrow();
        if (next.unfundedRequirements() >= prior.unfundedRequirements()) return false;
        for (int i = 0; i < prior.checkpoints().size(); i++)
            if (next.checkpoints().get(i).cashWithoutNewSales() < prior.checkpoints().get(i).cashWithoutNewSales()) return false;
        return true;
    }
    private EconomicPlanChoice() { }
}
