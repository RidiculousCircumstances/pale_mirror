package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceBioformExecutorTest {
    @Test
    void matchesSourceLaunchCostsCompositionAndIndividualProfileGuard() {
        ReferenceInfectionModel model = connectedModel(false);
        ReferenceHiveOrgan brood = model.createOrgan(3, 2, 100.0d, 3.0d, 1, ReferenceOrganKind.BROOD_SAC);

        ReferenceSwarm harvester = model.launchBioform(brood, ReferenceBioformKind.HARVESTER, 6, 4, -1,
                Map.of(ReferenceBioformKind.RAIDER, 8.0d));

        assertEquals(82.0d, brood.biomass());
        assertEquals(ReferenceBioformKind.HARVESTER, harvester.kind());
        assertEquals(Map.of(ReferenceBioformKind.HARVESTER, 1.0d), harvester.composition());
        assertEquals(28.0d, harvester.power());
        assertEquals(0.82d, harvester.speed());
        assertEquals(6, harvester.forageX());
        assertEquals(4, harvester.forageY());

        ReferenceSwarm assault = model.launchBioform(brood, ReferenceBioformKind.RAIDER, 6, 4, 9,
                Map.of(ReferenceBioformKind.RAIDER, 1.0d, ReferenceBioformKind.BREAKER, 1.0d));
        assertEquals(32.0d, brood.biomass());
        assertEquals(414.40000000000003d, assault.power());
        assertEquals(0.9d, assault.speed());
        assertEquals(ReferenceFormationPhase.SCREEN, assault.phase());
        assertEquals(2, model.swarms().size());
        assertNull(model.launchBioform(model.organs().get(1), ReferenceBioformKind.RAIDER, 0, 0, -1, null));

        ReferenceInfectionModel discrete = connectedModel(true);
        ReferenceHiveOrgan discreteBrood = discrete.createOrgan(3, 2, 100.0d, 3.0d, 1, ReferenceOrganKind.BROOD_SAC);
        assertThrows(IllegalArgumentException.class, () -> discrete.launchBioform(discreteBrood, ReferenceBioformKind.RAIDER, 6, 4, -1,
                Map.of(ReferenceBioformKind.RAIDER, 0.5d)));
    }

    private static ReferenceInfectionModel connectedModel(boolean discrete) {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, discrete);
        model.seedInfection(2, 2, 0.85d, 2, true);
        return model;
    }
}
