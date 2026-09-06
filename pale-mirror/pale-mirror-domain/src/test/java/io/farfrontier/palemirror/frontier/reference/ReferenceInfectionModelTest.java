package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenceInfectionModelTest {
    @Test
    void matchesSourceTerrainTissueNetworkSignalAndDamagedCoreMicroTrace() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        assertEquals(ReferenceBiome.BARREN, model.biomeAt(0, 0));
        assertEquals(ReferenceBiome.PLAINS, model.biomeAt(6, 0));
        assertEquals(ReferenceBiome.FOREST, model.biomeAt(0, 4));
        assertEquals(3229.498783707243d, model.ecosystem().totalOrganic());
        model.seedInfection(2, 2, 0.85d, 2, true);
        ReferenceHiveOrgan synapse = model.createOrgan(3, 2, 30.0d, 2.0d, 1, ReferenceOrganKind.SYNAPSE);
        ReferenceHiveOrgan isolated = model.createOrgan(6, 4, 44.0d, 1.0d, null, ReferenceOrganKind.BROOD_SAC);
        model.genomeLevel("synaptic_redundancy", 0.5d);

        assertEquals(0.43185528820753927d, model.infectionAt(1, 0));
        assertEquals(0.85d, model.infectionAt(2, 2));
        assertEquals(0.28d, model.infectionAt(6, 4));
        assertEquals(3, model.organs().size());
        assertFalse(synapse.feral());
        assertTrue(isolated.feral());
        assertEquals(0.8031605044501805d, model.pressureAt(2, 2));
        assertEquals(0.434395251168097d, model.pressureAt(0, 0));
        assertEquals(0.4165945840269912d, model.routeInfection(0, 0, 6, 4));
        assertEquals(2, model.networkComponents().cells().size());
        assertEquals(0, model.componentNear(model.networkComponents(), 3, 2));
        assertEquals(1, model.componentNear(model.networkComponents(), 6, 4));
        assertEquals(1.175d, model.adaptationMultiplier("signal_multiplier"));
        assertEquals(1.0d, model.signalAt(2, 2));
        assertEquals(1.0d, model.signalAt(3, 2));
        assertEquals(0.0d, model.signalAt(6, 4));
        assertEquals(145.0d, model.organNetworkProfile(synapse).get("component_biomass"));
        assertEquals(2.0d, model.organNetworkProfile(synapse).get("component_organs"));
        assertEquals(0.045454545454545456d, model.feralFraction());
        assertEquals(1.0d, model.signalMap().get(2).get(4));
        assertEquals(0.0d, model.signalMap().get(4).get(6));

        model.organs().get(1).vitality(0.0d);
        model.refreshFeralStatus();

        assertEquals(0.0d, model.signalAt(3, 2));
        assertTrue(model.organs().get(1).feral());
        assertTrue(synapse.feral());
        assertTrue(isolated.feral());
    }
}
