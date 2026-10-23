package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Compares departure with another planning review. Supply owns the net need and
 * its deadline (including selected local production); logistics supplies its
 * conservative journey forecast. Deferral is not a promise of future stock. */
public final class TripDispatchPolicy {
    public enum Cause { ECONOMIC_LOT, SUPPLY_DEADLINE, ACCUMULATING }
    public record Goods(int quantity, long plannedLot, SupplyPlanner.Demand demand, boolean uncoveredNeed) {
        public Goods { Objects.requireNonNull(demand);
            if (quantity < 1 || plannedLot < 0) throw new IllegalArgumentException("invalid trip demand"); }
    }
    public static Cause assess(List<Goods> manifest, long journeyTicks, long reviewTicks) {
        if (manifest.isEmpty()) throw new IllegalArgumentException("empty trip has no dispatch policy");
        if (journeyTicks < 0 || reviewTicks < 1)
            throw new IllegalArgumentException("invalid departure timing/capacity");
        // An old quote is not a consumption/production deadline. Permit even one
        // item when another review would lose the remaining delivery slack; this
        // also preserves partial recovery when every feasible journey is late.
        if (manifest.stream().anyMatch(g -> g.uncoveredNeed() && g.demand().purchase() > 0
                && g.demand().deadlineTicks() != Long.MAX_VALUE
                && (g.demand().deadlineTicks() <= journeyTicks
                    || g.demand().deadlineTicks() - journeyTicks <= reviewTicks))) return Cause.SUPPLY_DEADLINE;
        // A mixed cargo may share one trip. Fractions refer to each item's
        // economically chosen lot, not a fixed commodity load threshold.
        var numerator = java.math.BigInteger.ZERO;
        var denominator = java.math.BigInteger.ONE;
        for (var goods : manifest) {
            if (goods.plannedLot() == 0) continue;
            var lot = java.math.BigInteger.valueOf(goods.plannedLot());
            numerator = numerator.multiply(lot).add(denominator.multiply(java.math.BigInteger.valueOf(goods.quantity())));
            denominator = denominator.multiply(lot);
            if (numerator.compareTo(denominator) >= 0) return Cause.ECONOMIC_LOT;
        }
        // No immediate service benefit offsets a separate small journey while
        // waiting is safe. Re-evaluate live needs/offers next review rather than
        // spending a crew solely to satisfy a reserve rounding remainder.
        return Cause.ACCUMULATING;
    }
    /** Existing commitments cover the same need once. A reserve remainder is not a new emergency.
     * Input deadlines are already netted by the production planner; direct consumption is
     * checked at this route's horizon rather than the commodity's initial lead estimate. */
    public static boolean uncovered(SupplyPlanner.Demand demand, SupplyReservePlanner.Need need,
            long available, List<SupplyPlanner.Arrival> incoming, long arrivalTicks, long reviewTicks) {
        if (demand.purchase() == 0) return false;
        if (need.consumption().numerator().signum() == 0)
            return demand.urgentPurchase() > 0 || demand.deadlineTicks() != Long.MAX_VALUE
                    && (demand.reasons().contains(SupplyPlanner.Reason.PRODUCTION_INPUT)
                        || demand.reasons().contains(SupplyPlanner.Reason.PRODUCTION_FUEL));
        var arrivals = new ArrayList<>(incoming);
        if (demand.production() > 0 && demand.deadlineTicks() != Long.MAX_VALUE)
            arrivals.add(new SupplyPlanner.Arrival(demand.production(), demand.deadlineTicks()));
        return !DatedStockCoverage.assess(available, need, 0, arrivals,
                Math.addExact(arrivalTicks, reviewTicks)).covered();
    }
    private TripDispatchPolicy() { }
}
