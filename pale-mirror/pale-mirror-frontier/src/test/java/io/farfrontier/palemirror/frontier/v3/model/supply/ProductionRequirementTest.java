package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionRequirementTest {
    private static final SupplyPolicy POLICY = new SupplyPolicy(1000, 1, 64, 100, 500, false);
    @Test void recoveryInputKeepsItsMinimumAndDeadlineWithoutDoublingTheRequestedMaterial() {
        var stock = new SupplyPlanner.Stock(0, 0, 0, 4, 0, 0, 1, POLICY, List.of(), OptionalLong.empty(),
                Optional.of(new ProductionRequirement(1, 0)));
        var plan = new SupplyPlanner().plan(Map.of("material", stock), List.of());
        var demand = plan.require("material");
        assertEquals(4, demand.purchase());
        assertEquals(1, demand.protectedInput());
        assertEquals(0, demand.deadlineTicks());
        assertTrue(demand.reasons().contains(SupplyPlanner.Reason.CRITICAL_PRODUCTION_DEADLINE));
        assertTrue(plan.inputRequirements().isEmpty(), "owner material demand is already in the target, not another recipe input");
    }
    @Test void lateAdmittedRecoveryDoesNotCreateAnotherEmergencyEveryReview() {
        var stock = new SupplyPlanner.Stock(0, 1, 0, 1, 0, 0, 1, POLICY,
                List.of(new SupplyPlanner.Arrival(1, 1000)), OptionalLong.empty(), Optional.of(new ProductionRequirement(1, 0)));
        var demand = new SupplyPlanner().plan(Map.of("material", stock), List.of()).require("material");
        assertEquals(0, demand.purchase());
        assertEquals(0, demand.protectedInput());
        assertEquals(0, demand.urgentPurchase());
    }
}
