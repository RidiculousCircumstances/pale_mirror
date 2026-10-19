package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementBudget;
import java.util.*;

/** Quote-time adjustment of estimated envelopes, never a financial hold or spending authority. */
public final class EconomicPurchaseFunding {
    private EconomicPurchaseFunding() { }
    public static FixedScalar available(EconomicPlan plan, FixedScalar cash,
            List<ProcurementBudget.Need> needs, String item) {
        return available(plan, cash, needs, item, 0);
    }
    /** An immediate direct need may substitute for an unbacked recovery chain.
     * Alternative quotes share cash; only matching can reserve it, once. */
    public static FixedScalar available(EconomicPlan plan, FixedScalar cash,
            List<ProcurementBudget.Need> needs, String item, long immediateAlternativeQuantity) {
        if (immediateAlternativeQuantity < 0) throw new IllegalArgumentException("negative procurement alternative");
        var selected = needs.stream().filter(need -> need.item().equals(item)).findFirst();
        if (selected.isEmpty() || selected.orElseThrow().quantity() == 0) return FixedScalar.ZERO;
        // Forecast budgets use an estimated price, not an exclusive supplier. Reprice
        // alternatives under current consent, protecting earlier needs and a complete
        // same-deadline restart. Actual reservations reduce cash before the next match.
        var need = selected.orElseThrow();
        boolean restart = need.restartQuantity() > 0;
        var earlier = afterEarlierBudgets(plan, cash, needs, need.deadline());
        var peers = needs.stream().filter(other -> other.deadline() == need.deadline()).toList();
        var protectedCash = restart ? ProcurementBudget.forOptionalSpending(earlier, peers.stream()
                .filter(other -> !other.item().equals(item) && other.restartQuantity() > 0)
                .map(other -> new ProcurementBudget.Need(other.item(), other.restartQuantity(),
                        other.unitLimit(), other.deadline())).toList())
                : ProcurementBudget.forPurchase(earlier, peers, item);
        long estimate = plan.purchaseBudgets().getOrDefault(item, 0L);
        long optional = Math.max(0, cash.raw() - plan.purchaseBudgets().values().stream().reduce(0L, Math::addExact));
        // Only an immediate restart may displace fallback/reserve spending. Ordinary
        // purchases can use their envelope and genuinely unallocated liquidity.
        long envelope = restart ? cash.raw() : Math.min(cash.raw(), Math.addExact(estimate, optional));
        long ordinary = Math.min(envelope, protectedCash.raw());
        if (immediateAlternativeQuantity == 0) return new FixedScalar(ordinary);
        var backed = needs.stream().filter(other -> !other.item().equals(item)
                && other.deadline() <= need.deadline() && plan.fundedRestarts().contains(other.item()))
                .map(other -> new ProcurementBudget.Need(other.item(), other.restartQuantity(), other.unitLimit(), other.deadline())).toList();
        long substitutable = ProcurementBudget.forOptionalSpending(cash, backed).raw();
        long unit = plan.purchasePrices().get(item);
        long emergency = immediateAlternativeQuantity > substitutable / unit ? substitutable
                : Math.multiplyExact(immediateAlternativeQuantity, unit);
        return new FixedScalar(Math.max(ordinary, emergency));
    }
    private static FixedScalar afterEarlierBudgets(EconomicPlan plan, FixedScalar cash,
            List<ProcurementBudget.Need> needs, long deadline) {
        long left = cash.raw();
        for (var other : needs) {
            if (other.deadline() >= deadline || other.unitLimit().raw() == 0) continue;
            // The planner already chose between manufacturing and buying its output.
            // Gross fallback demand is not another independently funded obligation.
            long budget = Math.min(left, plan.purchaseBudgets().getOrDefault(other.item(), 0L));
            long cost = other.quantity() > budget / other.unitLimit().raw()
                    ? budget : other.quantity() * other.unitLimit().raw();
            left -= cost;
        }
        return new FixedScalar(left);
    }
}
