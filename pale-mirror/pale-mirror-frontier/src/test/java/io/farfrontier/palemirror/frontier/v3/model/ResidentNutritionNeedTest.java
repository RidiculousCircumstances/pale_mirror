package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResidentNutritionNeedTest {
    @Test void needIntegratesFractionalCanonicalTimeAndOneBreadRelievesOneUnit() {
        ResidentNutrition initial = ResidentNutrition.nourishedAt(0);
        ResidentNutrition beforeThreshold = initial.accrueThrough(23_999);
        assertEquals(0, beforeThreshold.hungerDeficit());
        assertEquals(23_999_000L, beforeThreshold.fractionalProgress());
        ResidentNutrition hungry = initial.accrueThrough(24_000);
        assertEquals(ResidentNutritionStatus.HUNGRY, hungry.status());
        assertEquals(1, hungry.hungerDeficit());
        assertEquals(hungry, hungry.accrueThrough(24_000));
        ResidentNutrition starving = hungry.accrueThrough(96_000);
        assertEquals(ResidentNutritionStatus.STARVING, starving.status());
        assertEquals(4, starving.hungerDeficit());
        ResidentNutrition fedOnce = starving.consumeBreadAt(96_000);
        assertEquals(3, fedOnce.hungerDeficit());
        assertEquals(4, fedOnce.lastIntegratedDay());
        assertEquals(ResidentNutritionStatus.STARVING, fedOnce.status());
        assertThrows(IllegalArgumentException.class, () -> initial.consumeBreadAt(0));
    }

    @Test void unequalRatesAndMidCycleChangePreserveElapsedFraction() {
        var rules = FrontierRuleset.ResidentLife.initial();
        ResidentNutrition slow = ResidentNutrition.nourishedAt(0).accrueThrough(12_000, rules, 500);
        ResidentNutrition fast = ResidentNutrition.nourishedAt(0).accrueThrough(12_000, rules, 2_000);
        assertEquals(0, slow.hungerDeficit());
        assertEquals(1, fast.hungerDeficit());
        assertEquals(6_000_000L, slow.fractionalProgress());
        assertEquals(48_000L, slow.nextThresholdTick(rules, 500));
        assertEquals(30_000L, slow.nextThresholdTick(rules, 1_000));
        assertEquals(1, slow.accrueThrough(30_000, rules, 1_000).hungerDeficit());
    }

    @Test void deficitIsBoundedAfterLongColdInterval() {
        ResidentNutrition cold = ResidentNutrition.nourishedAt(0).accrueThrough(24_000L * 10_000L);
        assertEquals(ResidentNutrition.MAX_HUNGER_UNITS, cold.hungerDeficit());
        assertEquals(10_000, cold.lastIntegratedDay());
    }

    @Test void selectedRulesetControlsNeedCadenceAndBreadRelief() {
        var rules = new FrontierRuleset.ResidentLife(24_000, 12_000,
                12_000L, 1, 3, 2, 10);
        var need = ResidentNutrition.nourishedAt(0).accrueThrough(36_000L, rules);
        assertEquals(3, need.hungerDeficit());
        assertEquals(ResidentNutritionStatus.STARVING, need.status());
        assertEquals(1, need.consumeBreadAt(36_000L, rules).hungerDeficit());
        assertEquals(10, ResidentNutrition.nourishedAt(0).accrueThrough(120_000L, rules).hungerDeficit());
    }
}
