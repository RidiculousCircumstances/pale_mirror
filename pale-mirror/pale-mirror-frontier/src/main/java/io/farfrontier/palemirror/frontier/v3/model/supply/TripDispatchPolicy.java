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
        if (manifest.stream().anyMatch(g -> g.waitingTicks() >= g.policy().maximumWaitTicks())) return Cause.MAXIMUM_WAIT;
        long loadPermille = manifest.stream().mapToLong(g -> Math.multiplyExact((long) g.quantity(), 1000L)
                / g.policy().preferredTripLoad()).sum();
        return loadPermille >= 1000 && manifest.stream().anyMatch(g -> g.quantity() >= g.policy().preferredLot())
                ? Cause.PREFERRED_LOAD : Cause.ACCUMULATING;
    }
    private TripDispatchPolicy() { }
}
