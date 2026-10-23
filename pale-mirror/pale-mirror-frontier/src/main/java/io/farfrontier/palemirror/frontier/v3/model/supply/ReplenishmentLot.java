package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.math.BigInteger;

/** Finite-horizon replenishment advice. Transport breakpoints, useful demand and
 * current funding determine the lot; no fixed minimum, new cash or stock promise. */
public final class ReplenishmentLot {
    public static long choose(long usefulQuantity, long cash, long unitPrice, LotTransportCost transport) {
        if (usefulQuantity < 0 || cash < 0 || unitPrice < 1) throw new IllegalArgumentException("invalid replenishment facts");
        long best = 0, bestExpense = 0;
        for (var option : transport.options()) {
            // Full charge intervals all have the same unit cost. Only the
            // first is needed; later full intervals tie and lock more capital.
            long limit = Math.min(usefulQuantity, Math.min(option.capacity(), option.unitsPerCharge()));
            if (limit == 0) continue;
            var expense = option.quote(limit);
            long quantity = Math.min(limit, Math.max(0, cash - expense.cash()) / unitPrice);
            if (quantity == 0) continue;
            int comparison = best == 0 ? -1 : BigInteger.valueOf(expense.economic()).multiply(BigInteger.valueOf(best))
                    .compareTo(BigInteger.valueOf(bestExpense).multiply(BigInteger.valueOf(quantity)));
            if (comparison < 0 || comparison == 0 && quantity < best) { best = quantity; bestExpense = expense.economic(); }

        }
        return best;
    }
    private ReplenishmentLot() { }
}
