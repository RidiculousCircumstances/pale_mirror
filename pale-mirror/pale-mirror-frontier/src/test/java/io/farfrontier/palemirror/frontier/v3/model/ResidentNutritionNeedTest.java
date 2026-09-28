package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResidentNutritionNeedTest {
    @Test void needIntegratesCanonicalDaysAndOneBreadRelievesOneUnit() {
        ResidentNutrition initial = ResidentNutrition.nourishedAt(0);
        assertEquals(initial, initial.accrueThrough(23_999));
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
