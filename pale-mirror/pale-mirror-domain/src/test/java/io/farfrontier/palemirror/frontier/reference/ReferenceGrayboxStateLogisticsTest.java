package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateLogisticsTest {
    @Test
    void restoresSitesAndTradeWithoutRetainingGeneratedLogisticsState() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        source.run(30);
        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
        ReferenceGrayboxStateRoot.State root = ReferenceGrayboxStateRoot.read(reference);

        ReferenceWorld restored = new ReferenceWorld(root.config());
        restored.rng().restore(root.rng());
        restored.populationRng().restore(root.populationRng());
        restored.day(root.day());
        restored.marketWorld().settlements().clear();
        ReferenceGrayboxStateSettlements.restore(reference.get("settlements"), restored.profile(), restored.populationRng())
                .values().forEach(restored.marketWorld()::addSettlement);
        restored.marketWorld().resourceSites().clear();
        ReferenceGrayboxStateResourceSites.restore(reference.get("resource_sites"), restored.settlements())
                .values().forEach(restored.marketWorld()::addResourceSite);
        ReferenceGrayboxStateTrade.read(reference.get("trade"), restored.profile(), restored.settlements()).applyTo(restored.trade());

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateResourceSites.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateResourceSites.capture(restored)));
        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateTrade.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateTrade.capture(restored)));
    }

    @Test
    void rejectsIncompleteSiteAndTradeOwnersBeforeTheyCanMutateTheWorld() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateResourceSites.restore(Map.of("$map", java.util.List.of(
                java.util.List.of(1, Map.of()))), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateTrade.read(Map.of(),
                ReferenceSimulationProfile.GRAYBOX_1_40, Map.of()));
    }
}
