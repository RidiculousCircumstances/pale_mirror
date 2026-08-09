package io.farfrontier.palemirror.internal.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.world.EncounterRecord;
import org.junit.jupiter.api.Test;

class TestMineMaterializationTranslatorTest {
    @Test
    void infectionPlanIsStableAndSeparatesOverlayFromController() {
        FacilityState facility = new FacilityState(new WorldObjectId("pale_mirror:test_mine"), 80, 10, 10);
        facility.infect();

        MaterializationPlan plan = new TestMineMaterializationTranslator().translate(facility);

        assertEquals(1, plan.desiredRevision());
        assertEquals(MaterializationOperationType.ENSURE_OVERLAY, plan.operations().getFirst().type());
        assertEquals(MaterializationOperationType.ENSURE_PM_ANCHOR, plan.operations().get(1).type());
        assertEquals("pale_mirror:test_mine:1:ensure_overlay", plan.operations().getFirst().idempotencyKey());
    }

    @Test
    void encounterSlotsBecomeIndependentPersistedOperations() {
        FacilityState facility = new FacilityState(new WorldObjectId("pale_mirror:test_mine"), 80, 10, 10);
        facility.infect();
        EncounterProfile profile = new EncounterProfile("pale_mirror:guards", 1,
                java.util.List.of(new EncounterProfile.ActorSlot("guard_1", "minecraft:zombie")));

        MaterializationPlan plan = new TestMineMaterializationTranslator().translate(facility, profile, EncounterRecord.none());

        assertEquals(3, plan.operations().size());
        assertEquals(MaterializationOperationType.ENSURE_CRIMSON_ENCOUNTER_ACTOR, plan.operations().get(2).type());
        assertEquals("guard_1", plan.operations().get(2).target());
        assertEquals("pale_mirror:test_mine:1:ensure_crimson_encounter_actor:guard_1", plan.operations().get(2).idempotencyKey());
    }
}
