package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateInfectionTest {
    @Test
    void restoresEveryInfectionOwnerAndTheExactDiscreteBodyLedgerAtEveryPinnedCheckpoint() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        Set<Integer> checkpoints = Set.of(0, 1, 2, 3, 5, 10, 15, 20, 25, 30);
        for (int day = 0; day <= 30; day++) {
            if (checkpoints.contains(day)) {
                Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                        ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
                Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
                ReferenceGrayboxStateRoot.State root = ReferenceGrayboxStateRoot.read(reference);
                ReferenceGrayboxStateInfection.State infection = ReferenceGrayboxStateInfection.read(reference.get("infection"), root.day());
                Map<Integer, Map<ReferenceBioformKind, List<String>>> identities = ReferenceGrayboxStateInfection.bioformIdentities(
                        document.get("bioform_identities"), infection.swarms());
                ReferenceWorld restored = new ReferenceWorld(root.config());
                infection.applyTo(restored.infection(), identities);
                assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateInfection.capture(source)),
                        ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateInfection.capture(restored)), "infection day " + day);
                assertEquals(ReferenceV2PublicSnapshot.canonicalJson(document.get("bioform_identities")),
                        ReferenceV2PublicSnapshot.canonicalJson(ReferenceGrayboxCanonicalState.capture(restored).get("bioform_identities")), "identities day " + day);
            }
            if (day < 30) source.tick();
        }
    }

    @Test
    void rejectsIncompleteInfectionOrBodyIdentityOwnersBeforeApplyingThem() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateInfection.read(Map.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateInfection.bioformIdentities(Map.of(), List.of()));
    }

    @Test
    void restoredInfectionContinuesItsNextEcologyAndHistoryStepWithoutReplayingSeedHistory() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        source.run(30);
        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
        ReferenceGrayboxStateRoot.State root = ReferenceGrayboxStateRoot.read(reference);
        ReferenceGrayboxStateInfection.State infection = ReferenceGrayboxStateInfection.read(reference.get("infection"), root.day());
        Map<Integer, Map<ReferenceBioformKind, List<String>>> identities = ReferenceGrayboxStateInfection.bioformIdentities(
                document.get("bioform_identities"), infection.swarms());
        ReferenceWorld restored = new ReferenceWorld(root.config());
        restored.populationRng().restore(root.populationRng());
        restored.marketWorld().settlements().clear();
        ReferenceGrayboxStateSettlements.restore(reference.get("settlements"), restored.profile(), restored.populationRng()).values()
                .forEach(restored.marketWorld()::addSettlement);
        restored.marketWorld().resourceSites().clear();
        ReferenceGrayboxStateResourceSites.restore(reference.get("resource_sites"), restored.settlements()).values()
                .forEach(restored.marketWorld()::addResourceSite);
        infection.applyTo(restored.infection(), identities);

        source.infection().ecologyStep(source.resourceSites().values(), 31);
        source.infection().recordEconomySnapshot(31, 365);
        restored.infection().ecologyStep(restored.resourceSites().values(), 31);
        restored.infection().recordEconomySnapshot(31, 365);

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateInfection.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateInfection.capture(restored)));
    }
}
