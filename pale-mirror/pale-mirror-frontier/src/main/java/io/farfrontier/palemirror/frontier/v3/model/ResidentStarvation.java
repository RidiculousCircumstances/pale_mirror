package io.farfrontier.palemirror.frontier.v3.model;

/** Separate health condition. Uses nutrition's integration interval, never another timer or vitality. */
public record ResidentStarvation(int severityUnits, long exposureRemainder, long recoveryRemainder) {
    public static final int MAX_SEVERITY_UNITS = 1_000;
    public static final ResidentStarvation NONE = new ResidentStarvation(0, 0, 0);

    public ResidentStarvation {
        if (severityUnits < 0 || severityUnits > MAX_SEVERITY_UNITS || exposureRemainder < 0
                || recoveryRemainder < 0 || exposureRemainder != 0 && recoveryRemainder != 0
                || severityUnits == 0 && recoveryRemainder != 0
                || severityUnits == MAX_SEVERITY_UNITS && exposureRemainder != 0)
            throw new IllegalArgumentException("invalid bounded starvation condition");
    }

    public ResidentStarvation integrateThrough(ResidentNutrition nutrition, long tick,
            FrontierRuleset.ResidentLife rules, int metabolismPermille) {
        nutrition.accrueThrough(nutrition.lastEvaluatedTick(), rules, metabolismPermille);
        var policy = rules.starvation();
        if (tick < nutrition.lastEvaluatedTick() || exposureRemainder >= policy.gainTicksPerUnit()
                || recoveryRemainder >= policy.recoveryTicksPerUnit())
            throw new IllegalArgumentException("starvation interval or remainder disagrees with world rules");
        long elapsed = tick - nutrition.lastEvaluatedTick();
        long recoveredFor = Math.min(elapsed, nutrition.ticksUntilBelow(policy.recoverAtOrAboveUnits(), rules, metabolismPermille));
        long emptyFor = Math.max(0, elapsed - nutrition.ticksUntilEmpty(rules, metabolismPermille));
        ResidentStarvation current = this;
        // Positive nutrition breaks a partial starvation exposure, without clearing severity.
        if (nutrition.satietyUnits() > 0 && exposureRemainder != 0)
            current = new ResidentStarvation(severityUnits, 0, recoveryRemainder);
        if (recoveredFor > 0) current = current.recover(recoveredFor, policy.recoveryTicksPerUnit());
        if (emptyFor > 0) current = current.expose(emptyFor, policy.gainTicksPerUnit());
        return current;
    }

    private ResidentStarvation recover(long elapsed, long perUnit) {
        if (severityUnits == 0) return NONE;
        long untilZero = Math.subtractExact(Math.multiplyExact((long) severityUnits, perUnit), recoveryRemainder);
        if (elapsed >= untilZero) return NONE;
        long total = Math.addExact(recoveryRemainder, elapsed);
        return new ResidentStarvation(severityUnits - (int) (total / perUnit), 0, total % perUnit);
    }

    private ResidentStarvation expose(long elapsed, long perUnit) {
        if (severityUnits == MAX_SEVERITY_UNITS) return new ResidentStarvation(severityUnits, 0, 0);
        long untilFull = Math.subtractExact(Math.multiplyExact((long) (MAX_SEVERITY_UNITS - severityUnits), perUnit), exposureRemainder);
        if (elapsed >= untilFull) return new ResidentStarvation(MAX_SEVERITY_UNITS, 0, 0);
        long total = Math.addExact(exposureRemainder, elapsed);
        return new ResidentStarvation(severityUnits + (int) (total / perUnit), total % perUnit, 0);
    }
}
