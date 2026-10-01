package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResidentNutritionNeedTest {
    @Test void nutritionUsesBoundedContentsAndConsumptionNotMovement() {
        var rules = FrontierRuleset.ResidentLife.initial();
        var full = ResidentNutrition.nourishedAtTick(0, rules);
        var onset = full.nextThresholdTick(rules, 1_000);
        assertEquals(23_976L, onset);
        assertFalse(full.accrueThrough(onset - 1, rules).wantsFood(rules));
        var hungry = full.accrueThrough(onset, rules);
        assertEquals(667, hungry.satietyUnits());
        assertTrue(hungry.wantsFood(rules));
        assertEquals(233, hungry.nutritionWanted(rules));
        var fed = hungry.consumeBreadAt(onset, rules);
        assertEquals(1_000, fed.satietyUnits());
        assertFalse(fed.wantsFood(rules));
        assertEquals(fed, fed.accrueThrough(onset, rules));
        assertThrows(IllegalArgumentException.class, () -> full.consumeBreadAt(0));
        assertThrows(IllegalArgumentException.class, () -> hungry.accrueThrough(onset - 1));
        assertThrows(IllegalArgumentException.class, () -> new ResidentNutrition(
                ResidentNutritionStatus.HUNGRY, rules.satietyCapacityUnits(), 0, 0).accrueThrough(0, rules));
        assertThrows(IllegalArgumentException.class, () -> new ResidentNutrition(
                ResidentNutritionStatus.STARVING, 0, 0, 1));
    }

    @Test void differentRatesAndSplitIntegrationHaveTheSameResult() {
        var rules = FrontierRuleset.ResidentLife.initial();
        var full = ResidentNutrition.nourishedAtTick(0, rules);
        var slow = full.accrueThrough(6_001, rules, 500);
        assertEquals(959, slow.satietyUnits());
        assertEquals(48_500L, slow.fractionalProgress());
        assertEquals(917, full.accrueThrough(3_000, rules, 2_000).satietyUnits());
        var split = full.accrueThrough(3_000, rules, 500).accrueThrough(6_001, rules, 500);
        assertEquals(slow, split);
        assertEquals(47_952L, full.nextThresholdTick(rules, 500));
        assertEquals(26_977L, slow.nextThresholdTick(rules, 1_000));
    }

    @Test void longStarvationDoesNotBecomeAnUnboundedMealDebt() {
        var rules = FrontierRuleset.ResidentLife.initial();
        var full = ResidentNutrition.nourishedAtTick(0, rules);
        var empty = full.accrueThrough(24_000L * 10_000L);
        assertEquals(0, empty.satietyUnits());
        assertEquals(0L, empty.fractionalProgress());
        assertEquals(ResidentNutritionStatus.STARVING, empty.status());
        assertEquals(rules.mealTargetUnits(), empty.nutritionWanted(rules));
        assertEquals(1_000, empty.consumeBreadAt(empty.lastEvaluatedTick()).satietyUnits());
        assertEquals(empty.consumeBreadAt(empty.lastEvaluatedTick()).satietyUnits(),
                full.accrueThrough(24_000).consumeBreadAt(24_000).satietyUnits());
        assertEquals(empty.lastEvaluatedTick() + rules.dayTicks(), empty.nextThresholdTick(rules, 1_000));
        assertEquals(0, full.accrueThrough(Long.MAX_VALUE, rules, 4_000).satietyUnits());
    }

    @Test void selectedRulesControlCapacityHysteresisAndFoodValue() {
        var rules = new FrontierRuleset.ResidentLife(24_000, 12_000, 100L, 30, 90, 50, 100);
        var full = ResidentNutrition.nourishedAtTick(0, rules);
        assertEquals(100, full.satietyUnits());
        assertEquals(7_100L, full.nextThresholdTick(rules, 1_000));
        var hungry = full.accrueThrough(7_100, rules);
        assertEquals(29, hungry.satietyUnits());
        assertEquals(61, hungry.nutritionWanted(rules));
        var once = hungry.consumeBreadAt(7_100, rules);
        assertEquals(79, once.satietyUnits());
        assertEquals(100, once.consumeBreadAt(7_100, rules).satietyUnits());
        assertThrows(IllegalArgumentException.class, () -> once.consumeNutritionAt(7_100, 0, rules, 1_000));
    }
}
