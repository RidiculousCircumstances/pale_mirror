package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceInfectionLegacyTest {
    @Test
    void retainedEntryPointsMatchSourceMutationTissueProjectAndSwarmSemantics() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        ReferenceHiveOrgan core = model.organs().get(1);
        core.biomass(200.0d);
        core.samples(10.0d);

        assertEquals(new ReferenceGridPosition(1, 2), model.nestGrowthTarget(core));
        assertEquals(ReferenceMutation.FIELD, model.chooseMutation(core));
        assertTrue(model.launchMutation(core, ReferenceMutation.FIELD, 3));
        assertTrue(model.launchLocalPropagation(core, 4));
        assertTrue(model.launchNestProject(core, new ReferenceGridPosition(4, 2), 5));

        assertEquals(140.0d, core.biomass());
        assertEquals(2.0d, core.samples());
        assertEquals(1, core.mutationLevel(ReferenceMutation.FIELD));
        assertEquals(5, core.lastProjectDay());
        assertEquals(1.0d, model.infectionAt(2, 2));
        assertEquals(0.536d, model.infectionAt(4, 2));
        ReferenceNestProject project = model.nestProjects().getFirst();
        assertEquals(ReferenceOrganKind.SYNAPSE, project.kind());
        assertEquals(39.6d, project.committedBiomass());

        ReferenceHiveOrgan brood = model.createOrgan(3, 2, 100.0d, 3.0d, 1, ReferenceOrganKind.BROOD_SAC);
        ReferenceSettlement prey = settlement(9, 5, 2, 100.0d);
        ReferenceSwarm swarm = model.launchSwarm(brood, Map.of(prey.id(), prey), prey.id(), 6);

        assertEquals(ReferenceBioformKind.RAIDER, swarm.kind());
        assertEquals(Map.of(ReferenceBioformKind.RAIDER, 1.0d), swarm.composition());
        assertEquals(370.0d, swarm.power());
        assertEquals(0.9d, swarm.speed());
        assertEquals(82.0d, brood.biomass());
        assertEquals(6, brood.lastProjectDay());
    }

    private static ReferenceSettlement settlement(int id, int x, int y, double population) {
        return new ReferenceSettlement(id, "Settlement " + id, x, y, population, 100.0d,
                new ReferenceNaturalPotential(), new ReferenceFacilities(), ReferenceSimulationProfile.SOURCE_V2);
    }
}
