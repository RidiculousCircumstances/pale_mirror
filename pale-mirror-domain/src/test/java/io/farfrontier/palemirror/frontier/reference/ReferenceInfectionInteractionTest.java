package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceInfectionInteractionTest {
    @Test
    void attackLeavesARecoverablePhysicalExploitationSiteInsteadOfAnAbstractReward() {
        ReferenceInfectionModel model = seeded(17L, 7, 5);
        ReferenceSettlement prey = settlement(9, 4, 2, 100.0d);
        double detritusBefore = model.ecosystem().cell(4, 2).detritus();
        ReferenceAttackEvent attack = new ReferenceAttackEvent(4, prey.id(), 100.0d, 1, ReferenceBioformKind.RAIDER,
                Map.of(ReferenceBioformKind.RAIDER, 1.0d), ReferenceFormationPhase.MAIN_ACTION);

        model.recordAttackHarvest(attack, prey, 10.0d, false, 12);

        assertEquals(0.04d, prey.illnessBurden());
        assertEquals(1, model.pendingExploitation().size());
        ReferenceExploitationSite site = model.pendingExploitation().getFirst();
        assertEquals(0.75d, site.mass());
        assertEquals(0.21999999999999997d, site.genes());
        assertEquals(new ReferenceGridPosition(4, 2), model.exploitationTarget(model.organs().get(1), 12));

        model.resolveExploitation(4, 2, model.organs().get(1), ReferenceBioformKind.HARVESTER);

        assertTrue(model.pendingExploitation().isEmpty());
        assertEquals(5.646721933745179d, model.ecosystem().cell(4, 2).detritus());
        assertEquals(4.4092219337451795d, detritusBefore);
    }

    @Test
    void tradeRefugeesAndDestroyedSettlementFollowTheirSourceVectorsAndAccounting() {
        ReferenceInfectionModel trade = seeded(6L, 7, 5);
        ReferenceSettlement seller = settlement(1, 2, 2, 100.0d);
        ReferenceSettlement buyer = settlement(2, 4, 2, 100.0d);
        int jumps = trade.afterTrade(Map.of(seller.id(), seller, buyer.id(), buyer),
                List.of(new ReferenceTradeRecord(3, seller.id(), buyer.id(), ReferenceResource.FOOD, 3.0d, 3.0d, 1.0d, List.of(1, 2))));
        trade.introduceRefugees(5, 2, 0.50d, 20.0d);

        assertEquals(1, jumps);
        assertEquals(2, trade.latentColonies().size());
        assertEquals(7.0d, trade.latentColonies().get(0).spores());
        assertEquals(0.18d, trade.latentColonies().get(0).strength());
        assertEquals(0.3d, trade.latentColonies().get(1).spores());
        assertEquals(0.175d, trade.latentColonies().get(1).strength());
        assertEquals(0.5d, trade.damageMemory().get("illness"));

        ReferenceInfectionModel destroyed = seeded(17L, 7, 5);
        destroyed.settlementDestroyed(settlement(3, 3, 2, 80.0d));
        assertEquals(119.08d, destroyed.organs().get(1).biomass());
        assertEquals(4.76d, destroyed.organs().get(1).samples());
        assertEquals(6.0d, destroyed.harvestedBiomass());
        assertEquals(1.7599999999999998d, destroyed.harvestedGeneticMaterial());
    }

    @Test
    void suppressionAndHotspotProjectionKeepTheirSourceOrderingAndThresholds() {
        ReferenceInfectionModel suppression = seeded(17L, 7, 5);
        assertEquals(2.085786437626905d, suppression.suppressArea(2, 2, 2.0d, 0.50d));
        assertEquals(95.0d, suppression.organs().get(1).biomass());
        assertEquals(37.5d, suppression.organs().get(1).vitality());
        assertEquals(0.5d, suppression.damageMemory().get("scorch"));

        ReferenceInfectionModel hotspots = new ReferenceInfectionModel(9, 9, 2L, 1.0d, false);
        hotspots.infectionAt(1, 1, 0.70d);
        hotspots.infectionAt(7, 1, 0.80d);
        hotspots.infectionAt(4, 4, 0.90d);
        hotspots.infectionAt(7, 7, 0.60d);
        hotspots.infectionAt(5, 4, 0.89d);
        assertEquals(List.of(new ReferenceInfectionHotspot(4, 4, 0.90d), new ReferenceInfectionHotspot(7, 1, 0.80d),
                new ReferenceInfectionHotspot(1, 1, 0.70d)), hotspots.hotspots(3, 3.0d));
        assertEquals(0.06172839506172839d, hotspots.infectedFraction());
    }

    private static ReferenceInfectionModel seeded(long seed, int width, int height) {
        ReferenceInfectionModel model = new ReferenceInfectionModel(width, height, seed, 1.0d, false);
        model.seedInfection(2, 2, 0.85d, 2, true);
        return model;
    }

    private static ReferenceSettlement settlement(int id, int x, int y, double population) {
        return new ReferenceSettlement(id, "Settlement " + id, x, y, population, 100.0d,
                new ReferenceNaturalPotential(), new ReferenceFacilities(), ReferenceSimulationProfile.SOURCE_V2);
    }
}
