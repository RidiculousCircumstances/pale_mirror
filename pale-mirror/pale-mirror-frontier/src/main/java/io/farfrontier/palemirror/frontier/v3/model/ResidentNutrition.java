package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Bounded stomach contents, never a debt of missed meals. Time is canonical simulation time. */
public record ResidentNutrition(ResidentNutritionStatus status, int satietyUnits,
                                long lastEvaluatedTick, long fractionalProgress) {
    public static final int MAX_SATIETY_UNITS = 1_000_000;
    public static final long HUNGER_UNIT_TICKS = 24_000L;
    public static final int STARVING_AFTER_MISSED_CYCLES = 3;

    public ResidentNutrition {
        Objects.requireNonNull(status, "resident nutrition status");
        if (satietyUnits < 0 || satietyUnits > MAX_SATIETY_UNITS || lastEvaluatedTick < 0 || fractionalProgress < 0
                || (satietyUnits == 0) != (status == ResidentNutritionStatus.STARVING)
                || satietyUnits == 0 && fractionalProgress != 0)
            throw new IllegalArgumentException("invalid bounded resident nutrition state");
    }

    public static ResidentNutrition nourishedAt(int cycle) {
        if (cycle < 0) throw new IllegalArgumentException("negative nutrition cycle");
        return nourishedAtTick(Math.multiplyExact((long) cycle, HUNGER_UNIT_TICKS));
    }

    public static ResidentNutrition nourishedAtTick(long tick) {
        return nourishedAtTick(tick, FrontierRuleset.ResidentLife.initial());
    }

    public static ResidentNutrition nourishedAtTick(long tick, FrontierRuleset.ResidentLife rules) {
        if (tick < 0) throw new IllegalArgumentException("negative nutrition instant");
        return at(rules.satietyCapacityUnits(), tick, 0L, rules);
    }

    public int resolvedCycle() { return Math.toIntExact(lastEvaluatedTick / HUNGER_UNIT_TICKS); }
    public int lastIntegratedDay() { return resolvedCycle(); }
    public boolean wantsFood(FrontierRuleset.ResidentLife rules) { return satietyUnits < rules.eatBelowUnits(); }
    public int nutritionWanted(FrontierRuleset.ResidentLife rules) {
        return Math.max(0, rules.mealTargetUnits() - satietyUnits);
    }

    public long ticksUntilEmpty(FrontierRuleset.ResidentLife rules, int rate) {
        validate(rules, rate);
        return Math.ceilDiv(Math.subtractExact(Math.multiplyExact((long) satietyUnits,
                Math.multiplyExact(rules.satietyUnitTicks(), 1_000L)), fractionalProgress), rate);
    }

    public long ticksUntilBelow(int thresholdUnits, FrontierRuleset.ResidentLife rules, int rate) {
        validate(rules, rate);
        if (thresholdUnits < 1 || thresholdUnits > rules.satietyCapacityUnits())
            throw new IllegalArgumentException("nutrition threshold exceeds stomach capacity");
        if (satietyUnits < thresholdUnits) return 0;
        return Math.ceilDiv(Math.subtractExact(Math.multiplyExact((long) (satietyUnits - thresholdUnits + 1),
                Math.multiplyExact(rules.satietyUnitTicks(), 1_000L)), fractionalProgress), rate);
    }

    /** Historical provision owner is inactive; these transitions retain its isolated fixtures. */
    public ResidentNutrition fed(int cycle) {
        requireNextCycle(cycle);
        return nourishedAt(cycle);
    }

    public ResidentNutrition missed(int cycle) {
        requireNextCycle(cycle);
        var rules = FrontierRuleset.ResidentLife.initial();
        int next = Math.max(0, satietyUnits - Math.ceilDiv(rules.satietyCapacityUnits(), STARVING_AFTER_MISSED_CYCLES));
        return at(next, Math.multiplyExact((long) cycle, HUNGER_UNIT_TICKS), 0L, rules);
    }

    public ResidentNutrition accrueThrough(long tick) { return accrueThrough(tick, FrontierRuleset.ResidentLife.initial()); }
    public ResidentNutrition accrueThrough(long tick, FrontierRuleset.ResidentLife rules) {
        return accrueThrough(tick, rules, ResidentCharacteristics.DEFAULT_METABOLISM_PERMILLE);
    }

    public ResidentNutrition accrueThrough(long tick, FrontierRuleset.ResidentLife rules, int metabolismPermille) {
        validate(rules, metabolismPermille);
        if (tick < lastEvaluatedTick) throw new IllegalArgumentException("nutrition cannot move backwards");
        long threshold = Math.multiplyExact(rules.satietyUnitTicks(), 1_000L);
        long untilEmpty = Math.ceilDiv(Math.subtractExact(Math.multiplyExact((long) satietyUnits, threshold),
                fractionalProgress), metabolismPermille);
        if (tick - lastEvaluatedTick >= untilEmpty) return at(0, tick, 0L, rules);
        long total = Math.addExact(fractionalProgress, Math.multiplyExact(tick - lastEvaluatedTick, (long) metabolismPermille));
        long depleted = total / threshold;
        int next = (int) Math.max(0L, satietyUnits - depleted);
        // Empty stomach does not retain imaginary negative contents or a repayment remainder.
        return at(next, tick, next == 0 ? 0L : total % threshold, rules);
    }

    /** Next semantic boundary, not a scheduled action for every nutritional unit. */
    public long nextThresholdTick(FrontierRuleset.ResidentLife rules, int metabolismPermille) {
        validate(rules, metabolismPermille);
        if (satietyUnits == 0) return Math.addExact(lastEvaluatedTick, rules.dayTicks());
        int units = wantsFood(rules) ? satietyUnits : satietyUnits - rules.eatBelowUnits() + 1;
        long remaining = Math.subtractExact(Math.multiplyExact((long) units,
                Math.multiplyExact(rules.satietyUnitTicks(), 1_000L)), fractionalProgress);
        return Math.addExact(lastEvaluatedTick, Math.ceilDiv(remaining, metabolismPermille));
    }

    public ResidentNutrition consumeBreadAt(long tick) { return consumeBreadAt(tick, FrontierRuleset.ResidentLife.initial()); }
    public ResidentNutrition consumeBreadAt(long tick, FrontierRuleset.ResidentLife rules) {
        return consumeBreadAt(tick, rules, ResidentCharacteristics.DEFAULT_METABOLISM_PERMILLE);
    }
    public ResidentNutrition consumeBreadAt(long tick, FrontierRuleset.ResidentLife rules, int metabolismPermille) {
        return consumeNutritionAt(tick, rules.breadNutritionUnits(), rules, metabolismPermille);
    }

    /** Only the exact food-consumption owner may invoke this after retiring the resource. */
    public ResidentNutrition consumeNutritionAt(long tick, int nutritionUnits,
            FrontierRuleset.ResidentLife rules, int metabolismPermille) {
        ResidentNutrition current = accrueThrough(tick, rules, metabolismPermille);
        if (nutritionUnits <= 0 || current.satietyUnits == rules.satietyCapacityUnits())
            throw new IllegalArgumentException("consumption has no nutritional effect");
        int next = (int) Math.min(rules.satietyCapacityUnits(), (long) current.satietyUnits + nutritionUnits);
        return at(next, tick, current.fractionalProgress, rules);
    }

    private void validate(FrontierRuleset.ResidentLife rules, int rate) {
        Objects.requireNonNull(rules, "nutrition rules");
        if (satietyUnits > rules.satietyCapacityUnits() || fractionalProgress >= Math.multiplyExact(rules.satietyUnitTicks(), 1_000L)
                || status != at(satietyUnits, lastEvaluatedTick, fractionalProgress, rules).status()
                || rate < ResidentCharacteristics.MIN_METABOLISM_PERMILLE || rate > ResidentCharacteristics.MAX_METABOLISM_PERMILLE)
            throw new IllegalArgumentException("nutrition disagrees with its capacity, fraction or metabolism");
    }
    private static ResidentNutrition at(int units, long tick, long remainder, FrontierRuleset.ResidentLife rules) {
        var status = units == 0 ? ResidentNutritionStatus.STARVING
                : units < rules.eatBelowUnits() ? ResidentNutritionStatus.HUNGRY : ResidentNutritionStatus.NOURISHED;
        return new ResidentNutrition(status, units, tick, remainder);
    }
    private void requireNextCycle(int cycle) {
        if (cycle <= resolvedCycle()) throw new IllegalArgumentException("nutrition cycle already resolved");
    }
}
