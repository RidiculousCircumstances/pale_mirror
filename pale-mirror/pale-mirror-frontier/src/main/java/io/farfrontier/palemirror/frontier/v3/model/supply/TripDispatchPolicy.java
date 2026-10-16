package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Batching is independent of ownership/admission. A preferred load is not an absolute minimum. */
public final class TripDispatchPolicy {
    public enum Cause { PREFERRED_LOAD, SUPPLY_DEADLINE, MAXIMUM_WAIT, ACCUMULATING }
    public record Goods(int quantity, long waitingTicks, SupplyPolicy policy, SupplyPlanner.Demand demand) {
        public Goods { Objects.requireNonNull(policy); Objects.requireNonNull(demand);
            if (quantity < 1 || waitingTicks < 0) throw new IllegalArgumentException("invalid trip demand"); }
    }
    public static Cause assess(List<Goods> manifest) {
        if (manifest.isEmpty()) throw new IllegalArgumentException("empty trip has no dispatch policy");
        if (manifest.stream().anyMatch(g -> g.demand().urgent(g.policy().leadTicks()))) return Cause.SUPPLY_DEADLINE;
        long loadPermille = manifest.stream().mapToLong(g -> Math.multiplyExact((long) g.quantity(), 1000L)
                / g.policy().preferredTripLoad()).sum();
        if (loadPermille >= 1000 && manifest.stream().anyMatch(g -> g.quantity() >= g.policy().preferredLot()))
            return Cause.PREFERRED_LOAD;
        // Age belongs to unmet demand, not to each newly produced item. An old
        // large order must not dispatch a fresh one-item caravan every review.
        // Permit a full smaller remaining need, or a useful lot, after waiting;
        // genuine supply deadlines above remain independent of batch size.
        if (manifest.stream().anyMatch(g -> g.waitingTicks() >= g.policy().maximumWaitTicks()
                && g.quantity() >= Math.min(g.policy().preferredLot(), Math.max(1, g.demand().purchase()))))
            return Cause.MAXIMUM_WAIT;
        return Cause.ACCUMULATING;
    }
    private TripDispatchPolicy() { }
}
