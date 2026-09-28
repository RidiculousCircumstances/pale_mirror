package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/**
 * Durable individual hunger deficit. During the resident-life cut, the retained day index
 * also keeps the legacy provision reducer decodable; only the new need owner may call
 * accrueThrough/consumeBreadAt after the provision schedule is retired.
 */
public record ResidentNutrition(ResidentNutritionStatus status, int consecutiveMissedCycles, int resolvedCycle) {
    public static final int STARVING_AFTER_MISSED_CYCLES = 3;
    /** First resident-life ruleset: one hunger unit accrues per canonical day. */
    public static final long HUNGER_UNIT_TICKS = 24_000L;
    public static final int MAX_HUNGER_UNITS = 255;

    public ResidentNutrition {
        Objects.requireNonNull(status, "resident nutrition status");
        if (consecutiveMissedCycles < 0 || consecutiveMissedCycles > MAX_HUNGER_UNITS || resolvedCycle < 0)
            throw new IllegalArgumentException("invalid resident nutrition counters");
        ResidentNutritionStatus expected = consecutiveMissedCycles == 0 ? ResidentNutritionStatus.NOURISHED
                : consecutiveMissedCycles < STARVING_AFTER_MISSED_CYCLES ? ResidentNutritionStatus.HUNGRY : ResidentNutritionStatus.STARVING;
        if (status != expected) throw new IllegalArgumentException("resident nutrition status disagrees with missed-cycle count");
    }

    public static ResidentNutrition nourishedAt(int cycle) {
        if (cycle < 0) throw new IllegalArgumentException("resident nutrition cycle must be non-negative");
        return new ResidentNutrition(ResidentNutritionStatus.NOURISHED, 0, cycle);
    }

    public int hungerDeficit() { return consecutiveMissedCycles; }
    public int lastIntegratedDay() { return resolvedCycle; }

    public ResidentNutrition fed(int cycle) {
        requireNextCycle(cycle);
        return nourishedAt(cycle);
    }

    public ResidentNutrition missed(int cycle) {
        requireNextCycle(cycle);
        int next = Math.min(MAX_HUNGER_UNITS, Math.addExact(consecutiveMissedCycles, 1));
        return new ResidentNutrition(next < STARVING_AFTER_MISSED_CYCLES ? ResidentNutritionStatus.HUNGRY : ResidentNutritionStatus.STARVING, next, cycle);
    }

    /** Deterministic lazy need integration; no per-tick scan or second timer is retained. */
    public ResidentNutrition accrueThrough(long canonicalTick) {
        return accrueThrough(canonicalTick, FrontierRuleset.ResidentLife.initial());
    }

    public ResidentNutrition accrueThrough(long canonicalTick, FrontierRuleset.ResidentLife rules) {
        Objects.requireNonNull(rules, "resident need rules");
        if (canonicalTick < 0) throw new IllegalArgumentException("hunger instant must be non-negative");
        long day = canonicalTick / rules.hungerUnitTicks();
        if (day > Integer.MAX_VALUE) throw new IllegalArgumentException("hunger day exceeds retained range");
        if (day <= resolvedCycle) return this;
        int next = (int) Math.min(rules.maxHungerUnits(), (long) consecutiveMissedCycles + day - resolvedCycle);
        return fromDeficit(next, (int) day);
    }

    /** One physically confirmed bread consumption relieves exactly one unit, once. */
    public ResidentNutrition consumeBreadAt(long canonicalTick) {
        return consumeBreadAt(canonicalTick, FrontierRuleset.ResidentLife.initial());
    }

    public ResidentNutrition consumeBreadAt(long canonicalTick, FrontierRuleset.ResidentLife rules) {
        Objects.requireNonNull(rules, "resident need rules");
        ResidentNutrition current = accrueThrough(canonicalTick, rules);
        if (current.consecutiveMissedCycles == 0) {
            throw new IllegalArgumentException("resident without hunger cannot consume a hunger allocation");
        }
        return fromDeficit(Math.max(0, current.consecutiveMissedCycles - rules.breadReliefUnits()), current.resolvedCycle);
    }

    private static ResidentNutrition fromDeficit(int deficit, int day) {
        return new ResidentNutrition(deficit == 0 ? ResidentNutritionStatus.NOURISHED
                : deficit < STARVING_AFTER_MISSED_CYCLES ? ResidentNutritionStatus.HUNGRY
                : ResidentNutritionStatus.STARVING, deficit, day);
    }

    private void requireNextCycle(int cycle) {
        if (cycle <= resolvedCycle) throw new IllegalArgumentException("resident nutrition may resolve one provisioning cycle only once");
    }
}
