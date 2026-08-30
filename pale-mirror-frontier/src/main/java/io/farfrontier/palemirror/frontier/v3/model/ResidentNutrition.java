package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/**
 * Durable individual food outcome.  A cycle is resolved once per resident, so a settlement
 * shortage cannot hide a cohort loss behind one aggregate fulfilment ratio.
 */
public record ResidentNutrition(ResidentNutritionStatus status, int consecutiveMissedCycles, int resolvedCycle) {
    public static final int STARVING_AFTER_MISSED_CYCLES = 3;

    public ResidentNutrition {
        Objects.requireNonNull(status, "resident nutrition status");
        if (consecutiveMissedCycles < 0 || resolvedCycle < 0) throw new IllegalArgumentException("invalid resident nutrition counters");
        ResidentNutritionStatus expected = consecutiveMissedCycles == 0 ? ResidentNutritionStatus.NOURISHED
                : consecutiveMissedCycles < STARVING_AFTER_MISSED_CYCLES ? ResidentNutritionStatus.HUNGRY : ResidentNutritionStatus.STARVING;
        if (status != expected) throw new IllegalArgumentException("resident nutrition status disagrees with missed-cycle count");
    }

    public static ResidentNutrition nourishedAt(int cycle) {
        if (cycle < 0) throw new IllegalArgumentException("resident nutrition cycle must be non-negative");
        return new ResidentNutrition(ResidentNutritionStatus.NOURISHED, 0, cycle);
    }

    public ResidentNutrition fed(int cycle) {
        requireNextCycle(cycle);
        return nourishedAt(cycle);
    }

    public ResidentNutrition missed(int cycle) {
        requireNextCycle(cycle);
        int next = Math.addExact(consecutiveMissedCycles, 1);
        return new ResidentNutrition(next < STARVING_AFTER_MISSED_CYCLES ? ResidentNutritionStatus.HUNGRY : ResidentNutritionStatus.STARVING, next, cycle);
    }

    private void requireNextCycle(int cycle) {
        if (cycle <= resolvedCycle) throw new IllegalArgumentException("resident nutrition may resolve one provisioning cycle only once");
    }
}
