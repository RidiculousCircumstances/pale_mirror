package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Exact resident hunger, including fractional elapsed progress at the last canonical evaluation. */
public record ResidentNutrition(ResidentNutritionStatus status, int hungerDeficit,
                                long lastEvaluatedTick, long fractionalProgress) {
    public static final int STARVING_AFTER_MISSED_CYCLES = 3;
    public static final long HUNGER_UNIT_TICKS = 24_000L;
    public static final int MAX_HUNGER_UNITS = 255;

    public ResidentNutrition {
        Objects.requireNonNull(status, "resident nutrition status");
        if (hungerDeficit < 0 || hungerDeficit > MAX_HUNGER_UNITS || lastEvaluatedTick < 0
                || fractionalProgress < 0)
            throw new IllegalArgumentException("invalid resident nutrition state");
        if (status != statusFor(hungerDeficit)) throw new IllegalArgumentException("resident nutrition status disagrees with deficit");
    }

    /** Source-fixture compatibility, not a second retained day clock. */
    public ResidentNutrition(ResidentNutritionStatus status, int deficit, int resolvedCycle) {
        this(status, deficit, Math.multiplyExact((long) resolvedCycle, HUNGER_UNIT_TICKS), 0L);
    }

    public static ResidentNutrition nourishedAt(int cycle) {
        if (cycle < 0) throw new IllegalArgumentException("resident nutrition cycle must be non-negative");
        return new ResidentNutrition(ResidentNutritionStatus.NOURISHED, 0,
                Math.multiplyExact((long) cycle, HUNGER_UNIT_TICKS), 0L);
    }

    public static ResidentNutrition nourishedAtTick(long tick) {
        return new ResidentNutrition(ResidentNutritionStatus.NOURISHED, 0, Math.max(0L, tick), 0L);
    }

    public int consecutiveMissedCycles() { return hungerDeficit; }
    public int resolvedCycle() { return Math.toIntExact(lastEvaluatedTick / HUNGER_UNIT_TICKS); }
    public int lastIntegratedDay() { return resolvedCycle(); }

    /** Legacy-only provision seam until the resident activity is the sole owner. */
    public ResidentNutrition fed(int cycle) {
        requireNextCycle(cycle);
        return nourishedAt(cycle);
    }

    /** Legacy-only provision seam until the resident activity is the sole owner. */
    public ResidentNutrition missed(int cycle) {
        requireNextCycle(cycle);
        int next = Math.min(MAX_HUNGER_UNITS, Math.addExact(hungerDeficit, 1));
        return new ResidentNutrition(statusFor(next), next,
                Math.multiplyExact((long) cycle, HUNGER_UNIT_TICKS), 0L);
    }

    public ResidentNutrition accrueThrough(long tick) {
        return accrueThrough(tick, FrontierRuleset.ResidentLife.initial());
    }

    public ResidentNutrition accrueThrough(long tick, FrontierRuleset.ResidentLife rules) {
        return accrueThrough(tick, rules, ResidentCharacteristics.DEFAULT_METABOLISM_PERMILLE);
    }

    public ResidentNutrition accrueThrough(long tick, FrontierRuleset.ResidentLife rules, int metabolismPermille) {
        Objects.requireNonNull(rules, "resident need rules");
        if (tick < lastEvaluatedTick || metabolismPermille < ResidentCharacteristics.MIN_METABOLISM_PERMILLE
                || metabolismPermille > ResidentCharacteristics.MAX_METABOLISM_PERMILLE)
            throw new IllegalArgumentException("invalid hunger evaluation tick or metabolism");
        long threshold = Math.multiplyExact(rules.hungerUnitTicks(), 1_000L);
        if (fractionalProgress >= threshold) throw new IllegalArgumentException("fraction exceeds the selected need rules");
        long total = Math.addExact(fractionalProgress,
                Math.multiplyExact(tick - lastEvaluatedTick, (long) metabolismPermille));
        long accrued = total / threshold;
        int next = (int) Math.min(rules.maxHungerUnits(), Math.addExact((long) hungerDeficit, accrued));
        return new ResidentNutrition(statusFor(next), next, tick, total % threshold);
    }

    public long nextThresholdTick(FrontierRuleset.ResidentLife rules, int metabolismPermille) {
        if (metabolismPermille < ResidentCharacteristics.MIN_METABOLISM_PERMILLE
                || metabolismPermille > ResidentCharacteristics.MAX_METABOLISM_PERMILLE)
            throw new IllegalArgumentException("invalid metabolism rate");
        long remaining = Math.subtractExact(Math.multiplyExact(rules.hungerUnitTicks(), 1_000L), fractionalProgress);
        if (remaining <= 0) throw new IllegalArgumentException("fraction exceeds the selected need rules");
        long delta = Math.floorDiv(Math.addExact(remaining, metabolismPermille - 1L), metabolismPermille);
        return Math.addExact(lastEvaluatedTick, delta);
    }

    public ResidentNutrition consumeBreadAt(long tick) {
        return consumeBreadAt(tick, FrontierRuleset.ResidentLife.initial());
    }

    public ResidentNutrition consumeBreadAt(long tick, FrontierRuleset.ResidentLife rules) {
        return consumeBreadAt(tick, rules, ResidentCharacteristics.DEFAULT_METABOLISM_PERMILLE);
    }

    public ResidentNutrition consumeBreadAt(long tick, FrontierRuleset.ResidentLife rules, int metabolismPermille) {
        ResidentNutrition current = accrueThrough(tick, rules, metabolismPermille);
        if (current.hungerDeficit == 0)
            throw new IllegalArgumentException("resident without hunger cannot consume a hunger allocation");
        int next = Math.max(0, current.hungerDeficit - rules.breadReliefUnits());
        return new ResidentNutrition(statusFor(next), next, tick, current.fractionalProgress);
    }

    private static ResidentNutritionStatus statusFor(int deficit) {
        return deficit == 0 ? ResidentNutritionStatus.NOURISHED
                : deficit < STARVING_AFTER_MISSED_CYCLES ? ResidentNutritionStatus.HUNGRY
                : ResidentNutritionStatus.STARVING;
    }

    private void requireNextCycle(int cycle) {
        if (cycle <= resolvedCycle()) throw new IllegalArgumentException("resident nutrition may resolve one provisioning cycle only once");
    }
}
