package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceBioformMovementTest {
    @Test
    void harvesterPhysicallyForagesReturnsAndAccountsForItsExactCargo() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        ReferenceHiveOrgan brood = model.createOrgan(3, 2, 100.0d, 3.0d, 1, ReferenceOrganKind.BROOD_SAC);
        model.digestEcology(7);
        ReferenceSwarm harvester = model.launchBioform(brood, ReferenceBioformKind.HARVESTER, 6, 4, -1, null);

        for (int day = 8; day <= 18; day++) model.advanceBioforms(Map.of(), day);

        assertEquals(0, model.swarms().size());
        assertEquals(2.0d, harvester.x());
        assertEquals(2.0d, harvester.y());
        assertEquals(32.0d, harvester.cargo());
        assertEquals(0.33537927271416795d, harvester.geneticCargo());
        assertEquals(148.69236838910945d, model.organs().get(1).biomass());
        assertEquals(3.6511697344077843d, model.organs().get(1).samples());
        assertEquals(61.39774572046349d, model.harvestedBiomass());
        assertEquals(0.6511697344077836d, model.harvestedGeneticMaterial());
        ReferenceNetworkFlow flow = model.networkFlows().getLast();
        assertEquals("harvester", flow.sourceKind());
        assertEquals(32.0d, flow.amount());
        assertEquals(21.76d, flow.retained());
        assertEquals("harvester:return", model.projectHistory().getLast().kind());
        assertEquals(18, model.projectHistory().getLast().day());
    }

    @Test
    void carrierArrivalCreatesOnlyTheSourceDefinedLatentColony() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        ReferenceHiveOrgan sporulator = model.createOrgan(3, 2, 100.0d, 3.0d, 1, ReferenceOrganKind.SPORULATOR);
        model.launchBioform(sporulator, ReferenceBioformKind.SPORE_CARRIER, 6, 4, -1, null);

        for (int day = 1; day <= 7; day++) model.advanceBioforms(Map.of(), day);

        assertEquals(0, model.swarms().size());
        assertEquals(1, model.latentColonies().size());
        ReferenceLatentColony latent = model.latentColonies().getFirst();
        assertEquals(6, latent.x());
        assertEquals(4, latent.y());
        assertEquals(8.0d, latent.spores());
        assertEquals(0.30d, latent.strength());
        assertEquals(2, latent.sourceOrganId());
        assertEquals(0.46d, model.infectionAt(6, 4));
    }
}
