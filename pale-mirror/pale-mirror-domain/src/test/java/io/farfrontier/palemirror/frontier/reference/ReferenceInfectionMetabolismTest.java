package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReferenceInfectionMetabolismTest {
    @Test
    void matchesSourceTissuePropagationAndFiniteEcologyMetabolism() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        model.createOrgan(3, 2, 30.0d, 2.0d, 1, ReferenceOrganKind.SYNAPSE);
        model.createOrgan(1, 2, 100.0d, 3.0d, 1, ReferenceOrganKind.DIGESTIVE_POOL);
        model.createOrgan(4, 2, 5.0d, 0.0d, 1, ReferenceOrganKind.BROOD_SAC);
        model.genomeLevel("rapid_digestion", 0.5d);

        model.advanceTissue();

        assertEquals(0.003345361300521016d, model.infectionAt(0, 0));
        assertEquals(0.6025226043785453d, model.infectionAt(1, 1));
        assertEquals(0.8525654749945741d, model.infectionAt(2, 2));
        assertEquals(0.0030026041943146805d, model.infectionAt(4, 4));

        model.digestEcology(11);

        assertEquals(126.99005760554527d, model.organs().get(1).biomass());
        assertEquals(3.2349952884501056d, model.organs().get(1).samples());
        assertEquals(29.620057605545284d, model.organs().get(2).biomass());
        assertEquals(109.68749135020991d, model.organs().get(3).biomass());
        assertEquals(9.27419940878383d, model.organs().get(4).biomass());
        assertEquals(34.56078224522662d, model.harvestedBiomass());
        assertEquals(0.36970969893509525d, model.harvestedGeneticMaterial());
        assertEquals(3194.085977295835d, model.ecosystem().totalOrganic());
        assertEquals(0.1133209805165043d, model.ecosystem().totalScar());
        assertEquals(22, model.networkFlows().size());
        ReferenceNetworkFlow first = model.networkFlows().getFirst();
        assertEquals("ecosystem", first.sourceKind());
        assertEquals(1, first.sourceId());
        assertEquals(3, first.organId());
        assertEquals(1.3597593091365252d, first.amount());
        assertEquals(1.3108079740076102d, first.retained());
        ReferenceNetworkFlow finalFlow = model.networkFlows().getLast();
        assertEquals("organ", finalFlow.sourceKind());
        assertEquals(1, finalFlow.sourceId());
        assertEquals(4, finalFlow.organId());
        assertEquals(5.139151248172766d, finalFlow.amount());
        assertEquals(4.954141803238546d, finalFlow.retained());
        ReferenceHiveEconomyEntry core = model.nestEconomy().get(1);
        assertEquals(22.509127246178704d, core.substrateIn());
        assertEquals(17.73915124817276d, core.biomassIncome());
        assertEquals(0.6099423944547162d, core.maintenance());
        ReferenceHiveEconomyEntry brood = model.nestEconomy().get(4);
        assertEquals(5.139151248172766d, brood.substrateIn());
        assertEquals(4.954141803238546d, brood.biomassIncome());
    }
}
