package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceHiveDirectorTest {
    @Test
    void directorPreparesFreshPerceptionThenLaunchesSeparateExploitationBeforeAssault() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        ReferenceHiveOrgan brood = model.createOrgan(3, 2, 100.0d, 3.0d, 1, ReferenceOrganKind.BROOD_SAC);
        ReferenceSettlement prey = settlement(9, 4, 2, 100.0d);
        model.recordAttackHarvest(new ReferenceAttackEvent(4, prey.id(), 100.0d, 1, ReferenceBioformKind.RAIDER,
                Map.of(ReferenceBioformKind.RAIDER, 1.0d), ReferenceFormationPhase.MAIN_ACTION), prey, 10.0d, false, 5);

        List<ReferenceHiveOrder> orders = new ReferenceHiveDirector().step(model, List.of(prey), 6);

        assertEquals(2, orders.size());
        assertEquals("exploit a successful assault through a separate biological operation", orders.getFirst().reason());
        assertEquals(ReferenceBioformKind.HARVESTER, orders.getFirst().bioformKind());
        assertEquals("screen and consume vulnerable human settlement", orders.get(1).reason());
        assertEquals(ReferenceBioformKind.RAIDER, orders.get(1).bioformKind());
        assertEquals(64.0d, brood.biomass());
        assertEquals(6, brood.lastProjectDay());
        assertTrue(model.pendingExploitation().isEmpty());
        assertEquals(2, model.swarms().size());
        assertEquals(ReferenceBioformKind.HARVESTER, model.swarms().get(0).kind());
        assertEquals(ReferenceBioformKind.RAIDER, model.swarms().get(1).kind());
    }

    @Test
    void v2ExploitationDoesNotTreatAnOrganOrCarrierPositionAsAConfirmedReport() {
        ReferenceInfectionModel model = new ReferenceInfectionModel(7, 5, 17L, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        model.createOrgan(3, 2, 100.0d, 3.0d, 1, ReferenceOrganKind.BROOD_SAC);
        ReferenceSettlement prey = settlement(9, 4, 2, 100.0d);
        model.recordAttackHarvest(new ReferenceAttackEvent(4, prey.id(), 100.0d, 1, ReferenceBioformKind.RAIDER,
                Map.of(ReferenceBioformKind.RAIDER, 1.0d), ReferenceFormationPhase.MAIN_ACTION), prey, 10.0d, false, 5);
        model.prepareHiveOrders(6);

        List<ReferenceHiveOrder> orders = new ReferenceHiveDirector().stepPerceived(model,
                ReferenceHiveWorldView.from(model, List.of(prey), 6), Set.of());

        assertTrue(orders.stream().noneMatch(item -> item.reason().contains("exploit a successful assault")));
        assertEquals(1, model.pendingExploitation().size());
    }

    private static ReferenceSettlement settlement(int id, int x, int y, double population) {
        return new ReferenceSettlement(id, "Settlement " + id, x, y, population, 100.0d,
                new ReferenceNaturalPotential(), new ReferenceFacilities(), ReferenceSimulationProfile.SOURCE_V2);
    }
}
