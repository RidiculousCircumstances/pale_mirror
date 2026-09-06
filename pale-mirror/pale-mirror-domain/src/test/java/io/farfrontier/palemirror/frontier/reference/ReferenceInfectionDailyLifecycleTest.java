package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceInfectionDailyLifecycleTest {
    @Test
    void fullDailyCycleMatchesSourceOrderAndKeepsSiteContaminationHumanVisible() {
        ReferenceInfectionModel model = seededModel();
        model.organs().get(1).biomass(200.0d);
        assertTrue(model.startMorphogenesis(model.organs().get(1), ReferenceOrganKind.SYNAPSE, 5, 2, 10));
        model.recordDamage("scorch", 0.04d);
        model.recordDamage("combat", 0.02d);
        ReferenceResourceSite near = new ReferenceResourceSite(1, ReferenceSiteKind.FARM, 2, 2, 0.9d, 5.0d, 1);
        near.contamination(0.10d);
        near.substrate(7.0d);
        ReferenceResourceSite far = new ReferenceResourceSite(2, ReferenceSiteKind.MINE, 6, 4, 0.8d, 4.0d, 2);
        far.contamination(0.12d);
        far.substrate(3.0d);

        model.ecologyStep(List.of(near, far), 11);
        model.recordEconomySnapshot(11, 365);

        assertEquals(0.0033422352794258764d, model.infectionAt(0, 0));
        assertEquals(0.8525642062440416d, model.infectionAt(2, 2));
        assertEquals(3195.6733696049396d, model.ecosystem().totalOrganic());
        assertEquals(0.09853925048522948d, model.ecosystem().totalScar());
        assertEquals(0.28d, near.contamination());
        assertEquals(0.0d, near.substrate());
        assertEquals(0.039999999999999994d, far.contamination());
        assertEquals(0.0d, far.substrate());
        assertEquals(1.0d, model.pressureAt(2, 2, 1));
        assertEquals(0.8031605044501805d, seededModel().pressureAt(2, 2));

        assertEquals(4, model.organs().size());
        assertEquals(128.01907254171638d, model.organs().get(1).biomass());
        assertEquals(45.3320580670965d, model.organs().get(2).biomass());
        assertEquals(106.93349935902235d, model.organs().get(3).biomass());
        assertEquals(17.17339140042983d, model.organs().get(4).biomass());
        assertFalse(model.organs().values().stream().anyMatch(ReferenceHiveOrgan::feral));
        assertEquals(3, model.nestProjects().getFirst().daysRemaining());
        assertEquals(23, model.networkFlows().size());
        assertEquals(0.0376d, model.damageMemory().get("scorch"));
        assertFalse(model.damageMemory().containsKey("combat"));

        ReferenceHiveEconomySnapshot core = model.nestEconomyHistory().getFirst();
        assertEquals(11, core.day());
        assertEquals(1, core.organId());
        assertEquals(145.0d, core.openingBiomass());
        assertEquals(19.572957900600926d, core.substrateIn());
        assertEquals(12.962347807953257d, core.biomassIncome());
        assertEquals(0.6099419329035046d, core.maintenance());
        assertEquals(128.01907254171638d, core.biomass());
    }

    @Test
    void snapshotKeepsNewPostEcologyOrganLedgerFieldsAbsentRatherThanInventingIncome() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(3, 3, 5L, 1.0d, false);
        ReferenceHiveOrgan organ = model.createOrgan(1, 1, null, null, null, ReferenceOrganKind.CORE);

        model.recordEconomySnapshot(4, 365);

        ReferenceHiveEconomySnapshot snapshot = model.nestEconomyHistory().getFirst();
        assertEquals(organ.id(), snapshot.organId());
        assertNull(snapshot.openingBiomass());
        assertNull(snapshot.substrateIn());
        assertEquals(115.0d, snapshot.biomass());
        assertThrows(IllegalStateException.class, () -> model.recordEconomySnapshot(4, 365));
    }

    private static ReferenceInfectionModel seededModel() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        model.createOrgan(3, 2, 30.0d, 2.0d, 1, ReferenceOrganKind.SYNAPSE);
        model.createOrgan(1, 2, 100.0d, 3.0d, 1, ReferenceOrganKind.DIGESTIVE_POOL);
        model.createOrgan(4, 2, 5.0d, 0.0d, 1, ReferenceOrganKind.BROOD_SAC);
        return model;
    }
}
