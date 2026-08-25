package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceV2TerritoryInitializationTest {
    @Test
    void sourceV2SeedBuildsExactSectorsCivicsRoutesAndPerception() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        ReferenceV2State v2 = world.v2();

        assertTrue(world.v2Enabled());
        assertEquals(176, v2.sectors().size());
        assertEquals(176, v2.sectorControl().size());
        assertEquals(12, v2.civics().size());
        assertEquals(12, v2.humanPerceptions().size());
        assertEquals("0:0", v2.sectors().keySet().stream().findFirst().orElseThrow());
        assertEquals("15:10", v2.sectors().keySet().stream().reduce((left, right) -> right).orElseThrow());

        ReferenceSettlementDoctrine firstDoctrine = v2.doctrines().get(1);
        assertEquals(0.65142660303187d, firstDoctrine.caution());
        assertEquals(0.46307191256717745d, firstDoctrine.solidarity());
        assertEquals(0.41017317174816126d, firstDoctrine.commercialDependence());
        assertEquals(0.28376783513190246d, firstDoctrine.militancy());
        assertEquals(0.6864751188985867d, firstDoctrine.casualtyTolerance());
        assertEquals(0.3731158630376135d, firstDoctrine.quarantineWillingness());

        ReferenceV2OperationalSector sector = v2.sectors().get("4:6");
        assertEquals(134.57607232705368d, sector.organicMass());
        assertEquals(0.6312499999999999d, sector.moisture());
        assertEquals(0.5494546366695177d, sector.infection());
        assertEquals(0.9375d, sector.hiveInfluence());
        assertEquals(0.4d, sector.infrastructureValue());
        assertEquals(List.of(ReferenceRouteKey.between(6, 10)), sector.routeKeys());
        assertEquals(ReferenceSectorControlState.CONTESTED, v2.sectorControl().get("4:6").state());
        assertEquals("unclaimed frontier", v2.sectorControl().get("4:6").reason());

        ReferenceV2OperationalSector routeSector = v2.sectors().get("4:2");
        assertEquals(133.15250845386439d, routeSector.organicMass());
        assertEquals(0.4285714285714286d, routeSector.humanAccess());
        assertEquals(2.133103834551135d, routeSector.infrastructureValue());
        assertEquals(List.of(ReferenceRouteKey.between(6, 10), ReferenceRouteKey.between(10, 11)), routeSector.routeKeys());
        assertEquals(List.of("1:1", "1:2", "1:3", "1:4", "2:1"), world.trade().routes().getFirst().sectorKeys());

        ReferenceV2HumanPerception firstPerception = v2.humanPerceptions().get(1);
        assertEquals(List.of("9:1", "10:1", "11:1", "9:2", "10:2", "11:2", "12:2", "9:3", "10:3", "11:3"),
                firstPerception.beliefs().keySet().stream().toList());
        ReferenceV2Belief settlementCore = firstPerception.beliefs().get("10:2");
        assertEquals(0.9d, settlementCore.confidence());
        assertEquals(99.56601935648118d, settlementCore.organicMass());
        assertEquals(1.2000000000000002d, settlementCore.infrastructureValue());
        assertEquals(ReferenceObservationSource.SETTLEMENT, settlementCore.source());

        assertEquals(List.of("15:1", "4:6"), v2.hivePerception().beliefs().keySet().stream().toList());
        ReferenceV2Belief hiveObservation = v2.hivePerception().beliefs().get("15:1");
        assertEquals(1.0d, hiveObservation.confidence());
        assertEquals(0.5353847437780572d, hiveObservation.infection());
        assertEquals(ReferenceObservationSource.TISSUE, hiveObservation.source());
    }

    @Test
    void v2UsesItsOwnStreamAndDisabledConfigurationFailsClosed() {
        ReferenceWorld legacy = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2));
        ReferenceWorld territorial = new ReferenceWorld(ReferenceWorldConfig.sourceV2());

        assertFalse(legacy.v2Enabled());
        assertThrows(IllegalStateException.class, legacy::v2);
        assertEquals(legacy.settlements().get(1).population(), territorial.settlements().get(1).population());
        assertEquals(legacy.settlements().get(1).cash(), territorial.settlements().get(1).cash());
        assertEquals(legacy.resourceSites().get(1).condition(), territorial.resourceSites().get(1).condition());
        assertEquals(legacy.trade().routes().getFirst().risk(), territorial.trade().routes().getFirst().risk());
    }
}
