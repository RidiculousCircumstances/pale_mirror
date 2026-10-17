package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementBudget;
import java.util.*;

/** Quote-time adjustment of estimated envelopes, never a financial hold or spending authority. */
public final class EconomicPurchaseFunding {
    private EconomicPurchaseFunding() { }
    public static FixedScalar available(EconomicPlan plan, FixedScalar cash,
            List<ProcurementBudget.Need> needs, String item) {
        var selected = needs.stream().filter(need -> need.item().equals(item)).findFirst();
        if (selected.isEmpty() || selected.orElseThrow().quantity() == 0) return FixedScalar.ZERO;
        // Forecast budgets use an estimated price, not an exclusive supplier. Reprice
        // alternatives under current consent, protecting earlier needs and a complete
        // same-deadline restart. Actual reservations reduce cash before the next match.
        var need = selected.orElseThrow();
        boolean restart = need.restartQuantity() > 0;
        var protectedCash = restart ? ProcurementBudget.forOptionalSpending(cash, needs.stream()
                .filter(other -> !other.item().equals(item) && (other.deadline() < need.deadline()
                        || other.deadline() == need.deadline() && other.restartQuantity() > 0))
                .map(other -> new ProcurementBudget.Need(other.item(), other.deadline() < need.deadline()
                        ? other.quantity() : other.restartQuantity(), other.unitLimit(), other.deadline())).toList())
                : ProcurementBudget.forPurchase(cash, needs, item);
        long estimate = plan.purchaseBudgets().getOrDefault(item, 0L);
        long optional = Math.max(0, cash.raw() - plan.purchaseBudgets().values().stream().reduce(0L, Math::addExact));
        // Only an immediate restart may displace fallback/reserve spending. Ordinary
        // purchases can use their envelope and genuinely unallocated liquidity.
        long envelope = restart ? cash.raw() : Math.min(cash.raw(), Math.addExact(estimate, optional));
        return new FixedScalar(Math.min(envelope, protectedCash.raw()));
    }
}
