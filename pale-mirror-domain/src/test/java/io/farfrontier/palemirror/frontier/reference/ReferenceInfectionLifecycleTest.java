package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceInfectionLifecycleTest {
    @Test
    void sourceMorphogenesisCommitsOnlyAfterItsExactDurationAndTissueThreshold() {
        ReferenceInfectionModel model = seededModel();
        model.organs().get(1).biomass(200.0d);

        assertTrue(model.startMorphogenesis(model.organs().get(1), ReferenceOrganKind.SYNAPSE, 4, 2, 9));
        assertEquals(145.0d, model.organs().get(1).biomass());
        assertEquals(4, model.nestProjects().getFirst().daysRemaining());
        for (int day = 10; day <= 13; day++) model.advanceMorphogenesis(day);

        assertEquals(0, model.nestProjects().size());
        assertEquals(2, model.organs().size());
        ReferenceHiveOrgan synapse = model.organs().get(2);
        assertEquals(ReferenceOrganKind.SYNAPSE, synapse.kind());
        assertEquals(39.6d, synapse.biomass());
        assertEquals(3.0d, synapse.samples());
        assertEquals(1, synapse.parentNestId());
        assertEquals("morphogenesis:synapse", model.projectHistory().getFirst().kind());
        assertEquals("organ:synapse", model.projectHistory().getLast().kind());
    }

    @Test
    void destroyedCoreAbortsItsProjectThenCullsWithoutAHiddenCompletion() {
        ReferenceInfectionModel model = seededModel();
        model.organs().get(1).biomass(200.0d);
        assertTrue(model.startMorphogenesis(model.organs().get(1), ReferenceOrganKind.SYNAPSE, 4, 2, 9));

        model.organs().get(1).vitality(0.0d);
        model.advanceMorphogenesis(10);
        model.removeDestroyedOrgans();

        assertEquals(0, model.nestProjects().size());
        assertEquals(0, model.organs().size());
        assertEquals("aborted_morphogenesis:synapse", model.projectHistory().get(1).kind());
        assertEquals("destroyed:core", model.projectHistory().getLast().kind());
        assertEquals(52.97501589873883d, model.ecosystem().cell(2, 2).detritus());
    }

    @Test
    void latentColonyMaturesOnlyFromLiveDistantSourceAndDamageMemoryDecays() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(25, 3, 19L, 1.0d, false);
        model.seedInfection(0, 1, 0.85d, 2, true);
        model.addLatentColony(24, 2, 6.0d, 0.5d, Map.of("burrowing", 1.0d), 1);

        model.advanceLatentColonies();

        assertEquals(0, model.latentColonies().size());
        assertEquals(0.4825d, model.infectionAt(24, 2));
        ReferenceHiveOrgan mature = model.organs().get(2);
        assertEquals(ReferenceOrganKind.CORE, mature.kind());
        assertEquals(38.0d, mature.biomass());
        assertEquals(0.0d, mature.samples());
        assertEquals(1, mature.parentNestId());
        assertEquals("spore_maturation:core", model.projectHistory().getLast().kind());

        ReferenceInfectionModel damage = new ReferenceInfectionModel(1, 1, 1L, 1.0d, false);
        damage.recordDamage("scorch", 0.04d);
        damage.recordDamage("combat", 0.02d);
        damage.decayDamageMemory();
        assertEquals(0.0376d, damage.damageMemory().get("scorch"));
        assertFalse(damage.damageMemory().containsKey("combat"));
    }

    private static ReferenceInfectionModel seededModel() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        return model;
    }
}
