package io.farfrontier.palemirror.internal.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.WorldObjectId;
import org.junit.jupiter.api.Test;

class TestMineMaterializationTranslatorTest {
    @Test
    void infectionPlanIsStableAndSeparatesOverlayFromController() {
        FacilityState facility = new FacilityState(new WorldObjectId("pale_mirror:test_mine"), 80, 10, 10);
        facility.infect();

        MaterializationPlan plan = new TestMineMaterializationTranslator().translate(facility);

        assertEquals(1, plan.desiredRevision());
        assertEquals(MaterializationOperationType.ENSURE_OVERLAY, plan.operations().getFirst().type());
        assertEquals(MaterializationOperationType.ENSURE_TEST_THREAT_CONTROLLER, plan.operations().get(1).type());
        assertEquals("pale_mirror:test_mine:1:ensure_overlay", plan.operations().getFirst().idempotencyKey());
    }
}
