package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/** Commodity-neutral advice, best first. No stock, task, reservation or execution authority. */
public record SupplyPriority(Tier tier, long deadlineTicks, long missing, long target)
        implements Comparable<SupplyPriority> {
    public enum Tier {
        CRITICAL(0), PRODUCTION(1), RESERVE(2);
        private final int rank;
        Tier(int rank) { this.rank = rank; }
    }
    public SupplyPriority {
        Objects.requireNonNull(tier);
        if (deadlineTicks < 0 || missing < 0 || target < 0)
            throw new IllegalArgumentException("negative resource priority facts");
    }
    public static SupplyPriority from(long missing, long target, Optional<SupplyPlanner.Demand> advice, long leadTicks) {
        Objects.requireNonNull(advice);
        if (leadTicks < 0) throw new IllegalArgumentException("negative supply lead time");
        var tier = advice.filter(demand -> demand.urgent(leadTicks)).isPresent() ? Tier.CRITICAL
                : advice.filter(demand -> demand.reasons().contains(SupplyPlanner.Reason.PRODUCTION_INPUT)
                    || demand.reasons().contains(SupplyPlanner.Reason.PRODUCTION_FUEL)).isPresent() ? Tier.PRODUCTION : Tier.RESERVE;
        return new SupplyPriority(tier, advice.map(SupplyPlanner.Demand::deadlineTicks).orElse(Long.MAX_VALUE), missing, target);
    }
    /** Expected work reduces staffing priority only; it neither creates nor reserves stock. */
    public SupplyPriority withAssigned(long quantity) {
        if (quantity < 0) throw new IllegalArgumentException("negative assigned supply");
        return new SupplyPriority(tier, deadlineTicks, Math.max(0, missing - quantity), target);
    }
    @Override public int compareTo(SupplyPriority other) {
        int covered = Boolean.compare(missing == 0, other.missing == 0);
        if (covered != 0) return covered;
        if (missing == 0) return 0;
        int urgency = Integer.compare(tier.rank, other.tier.rank);
        if (urgency != 0) return urgency;
        int deadline = Long.compare(deadlineTicks, other.deadlineTicks);
        if (deadline != 0) return deadline;
        // Compare shortage proportions exactly, without overflow or floating-point ties.
        return BigInteger.valueOf(other.missing).multiply(BigInteger.valueOf(Math.max(1, target)))
                .compareTo(BigInteger.valueOf(missing).multiply(BigInteger.valueOf(Math.max(1, other.target))));
    }
}
