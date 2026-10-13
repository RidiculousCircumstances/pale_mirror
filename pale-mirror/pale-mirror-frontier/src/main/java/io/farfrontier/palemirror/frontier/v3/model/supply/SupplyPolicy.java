package io.farfrontier.palemirror.frontier.v3.model.supply;

/** Immutable commodity policy; quantities are item units, time is simulation ticks. */
public record SupplyPolicy(int reorderPermille, int preferredLot, int preferredTripLoad,
                           long leadTicks, long maximumWaitTicks, boolean essential) {
    public SupplyPolicy {
        if (reorderPermille < 0 || reorderPermille > 1000 || preferredLot < 1
                || preferredTripLoad < 1 || leadTicks < 1 || maximumWaitTicks < 1)
            throw new IllegalArgumentException("invalid supply policy");
    }
    public String canonicalText() {
        return reorderPermille + ":" + preferredLot + ":" + preferredTripLoad + ":"
                + leadTicks + ":" + maximumWaitTicks + ":" + essential;
    }
}
