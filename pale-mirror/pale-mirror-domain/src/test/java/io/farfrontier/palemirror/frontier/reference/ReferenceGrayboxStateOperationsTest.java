package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateOperationsTest {
    @Test
    void restoresActiveAndTerminalOperationsAtEveryPinnedCheckpoint() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        Set<Integer> checkpoints = Set.of(0, 1, 2, 3, 5, 10, 15, 20, 25, 30);
        for (int day = 0; day <= 30; day++) {
            if (checkpoints.contains(day)) {
                Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                        ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
                Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
                ReferenceGrayboxStateOperations.State operations = ReferenceGrayboxStateOperations.read(reference.get("operations"));
                ReferenceWorld restored = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
                operations.applyTo(restored.operations());
                assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateOperations.capture(source)),
                        ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateOperations.capture(restored)), "operations day " + day);
            }
            if (day < 30) source.tick();
        }
    }

    @Test
    void rejectsAnIncompleteOperationManagerBeforeMutatingIt() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateOperations.read(Map.of()));
    }

    @Test
    void restoredNamedReconContinuesWithoutReplayingItsLaunch() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(41L));
        source.operations().launchHuman(source, ReferenceOperationKind.RECON, 1, ReferenceTargetRef.cell(10, 10));
        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
        ReferenceGrayboxStateRoot.State root = ReferenceGrayboxStateRoot.read(reference);
        ReferenceGrayboxStateOperations.State operations = ReferenceGrayboxStateOperations.read(reference.get("operations"));
        ReferenceWorld restored = new ReferenceWorld(root.config());
        restored.populationRng().restore(root.populationRng());
        restored.marketWorld().settlements().clear();
        ReferenceGrayboxStateSettlements.restore(reference.get("settlements"), restored.profile(), restored.populationRng()).values()
                .forEach(restored.marketWorld()::addSettlement);
        operations.applyTo(restored.operations());

        source.day(1); source.operations().step(source);
        restored.day(1); restored.operations().step(restored);

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateOperations.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateOperations.capture(restored)));
        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateSettlements.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateSettlements.capture(restored)));
    }
}
