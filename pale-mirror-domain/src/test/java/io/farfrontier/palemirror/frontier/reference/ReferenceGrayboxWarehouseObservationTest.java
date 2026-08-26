package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenceGrayboxWarehouseObservationTest {
    @Test
    void exactItemDepositsAndWithdrawalsEnterTheCanonicalStockOwnerOnce() {
        ReferenceGrayboxSimulation simulation = ReferenceGrayboxSimulation.create(42L);
        ReferenceGrayboxSnapshot before = simulation.snapshot();
        ReferenceGrayboxSnapshot.Warehouse warehouse = before.warehouses().getFirst();
        double initial = quantity(warehouse, ReferenceResource.ORE);

        ReferenceGrayboxObservationOutcome deposit = simulation.observe(new ReferenceGrayboxWarehouseObservation(
                ReferenceGrayboxWarehouseObservation.VERSION, "warehouse-test:deposit", before.stateRevision(), warehouse.settlementId(),
                ReferenceResource.ORE, 3));
        assertTrue(deposit.applied(), "a current physical deposit must enter the source economy through its typed warehouse boundary");
        ReferenceGrayboxSnapshot afterDeposit = simulation.snapshot();
        assertEquals(initial + 3.0d / 64.0d, quantity(afterDeposit.warehouses().getFirst(), ReferenceResource.ORE), 1.0e-9d,
                "three ordinary items are exactly three sixty-fourths of one canonical source unit");
        assertNotEquals(before.stateRevision(), afterDeposit.stateRevision(), "a physical stock movement must produce a new source revision");

        ReferenceGrayboxObservationOutcome withdrawal = simulation.observe(new ReferenceGrayboxWarehouseObservation(
                ReferenceGrayboxWarehouseObservation.VERSION, "warehouse-test:withdrawal", afterDeposit.stateRevision(), warehouse.settlementId(),
                ReferenceResource.ORE, -2));
        assertTrue(withdrawal.applied(), "a backed withdrawal must use the source market custody order rather than a Minecraft-side counter");
        ReferenceGrayboxSnapshot afterWithdrawal = simulation.snapshot();
        assertEquals(initial + 1.0d / 64.0d, quantity(afterWithdrawal.warehouses().getFirst(), ReferenceResource.ORE), 1.0e-9d);

    }

    @Test
    void warehouseObservationRejectsAStaleStockFactWithoutChangingTheCurrentWorld() {
        ReferenceGrayboxSimulation simulation = ReferenceGrayboxSimulation.create(42L);
        ReferenceGrayboxSnapshot before = simulation.snapshot();
        ReferenceGrayboxSnapshot.Warehouse warehouse = before.warehouses().getFirst();
        simulation.tick();
        ReferenceGrayboxSnapshot current = simulation.snapshot();

        ReferenceGrayboxObservationOutcome outcome = simulation.observe(new ReferenceGrayboxWarehouseObservation(
                ReferenceGrayboxWarehouseObservation.VERSION, "warehouse-test:stale", before.stateRevision(), warehouse.settlementId(),
                ReferenceResource.FOOD, 1));
        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE, outcome.status());
        assertEquals(current, simulation.snapshot(), "a stale container observation must not be silently rebased onto a changed market");
    }

    private static double quantity(ReferenceGrayboxSnapshot.Warehouse warehouse, ReferenceResource resource) {
        return warehouse.stockpiles().stream().filter(stockpile -> stockpile.resource().equals(resource.name().toLowerCase(java.util.Locale.ROOT)))
                .findFirst().orElseThrow().quantity();
    }
}
