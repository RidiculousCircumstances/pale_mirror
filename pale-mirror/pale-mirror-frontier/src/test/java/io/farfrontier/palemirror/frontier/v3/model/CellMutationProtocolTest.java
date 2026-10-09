package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class CellMutationProtocolTest {
    @Test void chainedFactsReplaceOnlyAnUncommittedObservationNeverAnAppliedEffect() {
        var external = CellMutationProtocol.Semantics.EXTERNAL_OBSERVATION;
        assertEquals(CellMutationProtocol.Resolution.CAPTURE, CellMutationProtocol.review(Optional.empty(), "air", false, external));
        assertEquals(CellMutationProtocol.Resolution.SUPERSEDE_CAPTURE, CellMutationProtocol.review(Optional.of("air"), "stone", false, external));
        assertEquals(CellMutationProtocol.Resolution.APPLY_CAPTURE, CellMutationProtocol.review(Optional.of("stone"), "stone", false, external));
        assertEquals(CellMutationProtocol.Resolution.FINISH_THEN_OBSERVE_SUCCESSOR,
                CellMutationProtocol.review(Optional.of("air"), "stone", true, external));
        assertEquals(CellMutationProtocol.Resolution.EFFECT_AMBIGUOUS,
                CellMutationProtocol.review(Optional.of("air"), "stone", false, CellMutationProtocol.Semantics.NON_REPLAYABLE_EFFECT));
        assertEquals(CellMutationProtocol.Resolution.EFFECT_AMBIGUOUS,
                CellMutationProtocol.review(Optional.of("air"), "stone", true, CellMutationProtocol.Semantics.NON_REPLAYABLE_EFFECT));
        assertThrows(IllegalArgumentException.class, () -> CellMutationProtocol.review(Optional.empty(), "stone", true, external));
    }
    @Test void receiptsForceCanonicalDurabilityBeforeChangingOrRetiringPhysicalEvidence() {
        var site = new SubjectId("site:1-wheat-field"); var cell = new ResourceFieldLayout.CellId(1);
        var before = new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.GROWING, 6, false, false);
        var hold = new ResourceFieldForeignChangeHeld(site, 1, 1, cell, before, "world:durable-observation");
        var after = new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.OBSTRUCTED, ResourceFieldCycle.Crop.OBSTRUCTED, 0, false, false);
        var observed = new ResourceFieldForeignCellObserved(hold, after, "minecraft:stone", "minecraft:air");
        var acknowledged = new ResourceFieldForeignChangeAcknowledged(hold, after);
        for (CellMutationReceipt receipt : java.util.List.of(observed, acknowledged)) {
            assertTrue(receipt.requiresDurableBeforeEffect());
            assertEquals(ResourceSiteState.mutationKey(site, cell), receipt.mutationKey());
            assertEquals(hold.causationId(), receipt.mutationCause());
        }
    }
    @Test void addressesFenceOneCellWithExplicitFamilyAndExactReleaseEvidence() {
        var site = new SubjectId("site:1-wheat-field");
        var a = new CellMutationKey(CellMutationKey.OwnerFamily.RESOURCE_SITE, site, 1);
        var b = new CellMutationKey(CellMutationKey.OwnerFamily.RESOURCE_SITE, site, 2);
        var one = CellMutationProtocol.reserve(Map.of(), a, "first-cause");
        var two = CellMutationProtocol.reserve(one, b, "neighbor-cause");
        assertThrows(IllegalArgumentException.class, () -> CellMutationProtocol.reserve(two, a, "another-cause"));
        assertThrows(IllegalArgumentException.class, () -> CellMutationProtocol.release(two, a, "another-cause"));
        assertEquals(Map.of(b, "neighbor-cause"), CellMutationProtocol.release(two, a, "first-cause"));
        assertThrows(IllegalArgumentException.class, () -> CellMutationKey.OwnerFamily.decode(255));
    }
}
