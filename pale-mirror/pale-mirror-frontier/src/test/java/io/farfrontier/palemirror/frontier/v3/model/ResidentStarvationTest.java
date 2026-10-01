package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentStarvationTest {
    private final FrontierRuleset.ResidentLife rules = FrontierRuleset.ResidentLife.initial();

    @Test void anIntervalIsSplitAtEmptyStomachWithoutRetroactiveStarvation() {
        var full = ResidentNutrition.nourishedAtTick(0, rules);
        assertEquals(ResidentStarvation.NONE, ResidentStarvation.NONE.integrateThrough(full, 72_000, rules, 1_000));
        assertEquals(100, ResidentStarvation.NONE.integrateThrough(full, 96_000, rules, 1_000).severityUnits());
        var atEmpty = full.accrueThrough(72_000, rules);
        assertEquals(ResidentStarvation.NONE.integrateThrough(full, 96_000, rules, 1_000),
                ResidentStarvation.NONE.integrateThrough(atEmpty, 96_000, rules, 1_000));
        assertEquals(0, ResidentStarvation.NONE.integrateThrough(full, 36_000, rules, 2_000).severityUnits());
    }

    @Test void eatingRestoresContentsButDoesNotInstantlyRemoveCondition() {
        var empty = new ResidentNutrition(ResidentNutritionStatus.STARVING, 0, 0, 0);
        var condition = ResidentStarvation.NONE.integrateThrough(empty, 24_000, rules, 1_000);
        var fed = empty.consumeBreadAt(24_000, rules);
        assertEquals(condition, condition.integrateThrough(fed, 24_000, rules, 1_000));
        assertEquals(99, condition.integrateThrough(fed, 24_120, rules, 1_000).severityUnits());
        assertEquals(ResidentStarvation.NONE, condition.integrateThrough(fed, 36_000, rules, 1_000));
        var low = new ResidentNutrition(ResidentNutritionStatus.HUNGRY, 500, 24_000, 0);
        assertEquals(condition, condition.integrateThrough(low, 24_120, rules, 1_000));
    }

    @Test void fractionalExposureAndRecoverySurviveSplittingAndSaturateSafely() {
        var empty = new ResidentNutrition(ResidentNutritionStatus.STARVING, 0, 0, 0);
        var partial = ResidentStarvation.NONE.integrateThrough(empty, 239, rules, 1_000);
        assertEquals(0, partial.severityUnits()); assertEquals(239, partial.exposureRemainder());
        assertEquals(new ResidentStarvation(1, 0, 0), partial.integrateThrough(empty.accrueThrough(239), 240, rules, 1_000));
        assertEquals(ResidentStarvation.MAX_SEVERITY_UNITS,
                ResidentStarvation.NONE.integrateThrough(empty, Long.MAX_VALUE, rules, 1_000).severityUnits());
        var full = ResidentNutrition.nourishedAtTick(0);
        var damaged = new ResidentStarvation(100, 0, 0);
        var recovered = damaged.integrateThrough(full, 119, rules, 1_000);
        assertEquals(damaged.integrateThrough(full, 121, rules, 1_000),
                recovered.integrateThrough(full.accrueThrough(119), 121, rules, 1_000));
        assertThrows(IllegalArgumentException.class, () -> new ResidentStarvation(1, 240, 0)
                .integrateThrough(empty, 1, rules, 1_000));
    }

    @Test void diseaseTransitionsCannotEraseIndependentStarvation() {
        var condition = new ResidentStarvation(10, 5, 0);
        var health = new ResidentHealth(ResidentHealthStatus.HEALTHY, 0, condition);
        assertEquals(condition, health.transition(ResidentHealthStatus.EXPOSED, 1).starvation());
    }
}
