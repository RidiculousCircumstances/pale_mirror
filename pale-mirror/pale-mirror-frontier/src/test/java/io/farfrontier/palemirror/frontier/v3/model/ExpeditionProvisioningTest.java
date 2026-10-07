package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionProvisioningTest {
    private static final SubjectId CARRIER = new SubjectId("resident:carrier"), COMPANION = new SubjectId("resident:companion");
    private static final FrontierRuleset.ResidentLife LIFE = FrontierRuleset.ResidentLife.initial();
    private static final FoodCatalog.Food FOOD = LIFE.foods().require(FoodCatalog.BREAD);
    private static final ExpeditionRules RULES = ExpeditionRules.initial();

    @Test void forecastCountsReturnAndMarginAndVariedMetabolismNotJustOutwardDistance() {
        var slow = new ExpeditionProvisioning.Member(CARRIER, LIFE.eatBelowUnits(), LIFE.metabolismMinPermille(), 0, 1);
        var fast = new ExpeditionProvisioning.Member(COMPANION, LIFE.eatBelowUnits(), LIFE.metabolismMaxPermille(), 0, 0);
        var plan = ExpeditionProvisioning.plan(List.of(slow, fast), 500, 500, 1, 1000, true, FOOD, LIFE, RULES);
        assertEquals(RULES.plannedDuration(500, 500), plan.durationTicks());
        assertTrue(plan.feasible());
        assertTrue(plan.members().get(COMPANION).requiredFoodItems() > plan.members().get(CARRIER).requiredFoodItems());
        var longer = ExpeditionProvisioning.plan(List.of(slow, fast), 500, 1500, 1, 1000, true, FOOD, LIFE, RULES);
        assertTrue(longer.foodItems() > plan.foodItems());
        assertEquals(ExpeditionProvisioning.Refusal.FOOD_STOCK, ExpeditionProvisioning.plan(
                List.of(slow, fast), 500, 500, 1, plan.foodItems() - 1, true, FOOD, LIFE, RULES).refusal());
    }

    @Test void shortTripWithFullActualCarrierNeedsAnimalEvenWhenCompanionHasSparePockets() {
        var carrier = new ExpeditionProvisioning.Member(CARRIER, LIFE.satietyCapacityUnits(), 1000, 10, 1);
        var companion = new ExpeditionProvisioning.Member(COMPANION, LIFE.satietyCapacityUnits(), 1000, 0, 0);
        var plan = ExpeditionProvisioning.plan(List.of(carrier, companion), 1, 1, 1, 100, true, FOOD, LIFE, RULES);
        assertEquals(ExpeditionProvisioning.Transport.PACK_ANIMAL, plan.transport());
        assertTrue(plan.feasible());
        assertEquals(ExpeditionProvisioning.Refusal.NO_TRANSPORT, ExpeditionProvisioning.plan(
                List.of(carrier, companion), 1, 1, 1, 100, false, FOOD, LIFE, RULES).refusal());
        assertThrows(IllegalArgumentException.class, () -> ExpeditionProvisioning.plan(
                List.of(carrier, companion), 1, 1, 2, 100, true, FOOD, LIFE, RULES));
    }

    @Test void longerWalkDoesNotRequireAnAnimalIfItsActualLoadFits() {
        var carrier = new ExpeditionProvisioning.Member(CARRIER, LIFE.satietyCapacityUnits(), 1000, 0, 1);
        var plan = ExpeditionProvisioning.plan(List.of(carrier), 1000, 1000, 1, 1000, false, FOOD, LIFE, RULES);
        assertEquals(ExpeditionProvisioning.Transport.WALKING, plan.transport());
        assertTrue(plan.feasible());
        assertEquals(0, plan.sharedFoodItems());
        assertEquals(plan.foodItems(), plan.members().get(CARRIER).carriedFoodItems());
    }

    @Test void existingPersonalFoodDoesNotRequireDuplicateWarehouseStockOrAnotherStackSlot() {
        var empty = new ExpeditionProvisioning.Member(CARRIER, LIFE.eatBelowUnits(), 1000, 0, 1);
        var initial = ExpeditionProvisioning.plan(List.of(empty), 500, 500, 1, 1000, false, FOOD, LIFE, RULES);
        int required = initial.foodItems();
        assertTrue(required > 0 && required < 64);
        var loaded = new ExpeditionProvisioning.Member(CARRIER, LIFE.eatBelowUnits(), 1000, 9, 1,
                required, 64 - required);
        var plan = ExpeditionProvisioning.plan(List.of(loaded), 500, 500, 1, 0, false, FOOD, LIFE, RULES);
        assertTrue(plan.feasible()); assertEquals(ExpeditionProvisioning.Transport.WALKING, plan.transport());
        assertEquals(0, plan.foodToLoad());
        var partial = new ExpeditionProvisioning.Member(CARRIER, LIFE.eatBelowUnits(), 1000, 9, 1,
                required - 1, 64 - required + 1);
        var refill = ExpeditionProvisioning.plan(List.of(partial), 500, 500, 1, 1, false, FOOD, LIFE, RULES);
        assertTrue(refill.feasible()); assertEquals(1, refill.foodToLoad());
        assertThrows(IllegalArgumentException.class, () -> new ExpeditionProvisioning.Member(CARRIER, 0, 1000, 0, 0, 1, 63));
    }
}
