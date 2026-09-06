package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxStateMarketTest {
    @Test
    void restoresPrivateEconomicLedgersWithoutRebootstrappingTheMarket() {
        ReferenceWorld source = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        source.run(30);
        Map<String, Object> document = ReferenceGrayboxStateDocument.decode(
                ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(source)));
        Map<String, Object> reference = ReferenceGrayboxStateReader.referenceState(document.get("reference_state"));
        ReferenceGrayboxStateRoot.State root = ReferenceGrayboxStateRoot.read(reference);
        ReferenceWorld restored = new ReferenceWorld(root.config());
        restored.rng().restore(root.rng()); restored.populationRng().restore(root.populationRng()); restored.day(root.day());
        restored.marketWorld().settlements().clear();
        ReferenceGrayboxStateSettlements.restore(reference.get("settlements"), restored.profile(), restored.populationRng()).values()
                .forEach(restored.marketWorld()::addSettlement);
        restored.marketWorld().resourceSites().clear();
        ReferenceGrayboxStateResourceSites.restore(reference.get("resource_sites"), restored.settlements()).values()
                .forEach(restored.marketWorld()::addResourceSite);
        ReferenceGrayboxStateTrade.read(reference.get("trade"), restored.profile(), restored.settlements()).applyTo(restored.trade());

        ReferenceGrayboxStateMarket.read(reference.get("market"), restored.profile(), restored.settlements(), restored.resourceSites(),
                reference.get("resource_sites"), restored.day()).applyTo(restored.microeconomy(), restored.settlements().keySet());

        assertEquals(ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateMarket.capture(source)),
                ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateMarket.capture(restored)));
    }

    @Test
    void rejectsAnIncompleteMarketBeforeItCanReplaceTheLiveOwner() {
        assertThrows(IllegalArgumentException.class, () -> ReferenceGrayboxStateMarket.read(Map.of(),
                ReferenceSimulationProfile.GRAYBOX_1_40, Map.of(), Map.of(), Map.of("$map", java.util.List.of()), 0));
    }
}
