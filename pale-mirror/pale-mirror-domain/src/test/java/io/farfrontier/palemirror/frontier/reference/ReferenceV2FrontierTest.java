package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceV2FrontierTest {
    @Test
    void unheldClearanceRecontaminatesButSuppliedCampaignGarrisonHoldsTheSector() {
        ReferenceWorld world = sourceWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceV2State v2 = world.v2();
        String key = v2.sectorKeyAt(settlement.x() + 4, settlement.y());
        ReferenceV2OperationalSector sector = v2.sectors().get(key);
        for (ReferenceGridPosition cell : sector.cells()) world.infection().infectionAt(cell.x(), cell.y(), .02d);
        ReferenceGridPosition source = sector.cells().getFirst();
        world.infection().latentColonies.add(new ReferenceLatentColony(source.x(), source.y(), 10.0d, .5d, Map.of(), null));
        ReferenceV2SectorControl control = v2.sectorControl().get(key);
        control.lastClearedDay(world.day() - 30);
        control.state(ReferenceSectorControlState.HUMAN);
        v2.refreshTerritory(world);
        v2.advanceFrontier(world);
        v2.refreshTerritory(world);
        assertEquals(.026299999999999994d, v2.sectors().get(key).infection());

        ReferenceFrontCampaign campaign = campaign(99, settlement.id(), key, ReferenceFrontPhase.HOLD,
                Map.of(settlement.id(), 12.0d), Map.of(), Map.of());
        v2.mutableFrontCampaigns().put(campaign.id(), campaign);
        v2.mutableSupplyLines().put(campaign.id(), ReferenceV2Frontier.supplyLine(v2, world, campaign));
        v2.updateSectorControl(world);
        assertEquals(12.0d, v2.sectorControl().get(key).garrison());
        assertTrue(v2.sectorControl().get(key).supplied());
        assertEquals(.192d, v2.sectorControl().get(key).cordonStrength());
        assertEquals(.044471d, v2.supplyLines().get(campaign.id()).risk());
        assertEquals(.9703526666666666d, v2.supplyLines().get(campaign.id()).readiness());
    }

    @Test
    void coordinateBioformAttackConsumesCordonInsteadOfCreatingASettlementAttack() {
        ReferenceWorld world = sourceWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceV2State v2 = world.v2();
        String key = v2.sectorKeyAt(settlement.x(), settlement.y());
        ReferenceV2SectorControl control = v2.sectorControl().get(key);
        control.state(ReferenceSectorControlState.HUMAN);
        control.cordonStrength(.7d);
        control.garrison(18.0d);
        ReferenceSwarm swarm = new ReferenceSwarm(999, settlement.x(), settlement.y(), 60.0d, -1, .9d,
                ReferenceBioformKind.RAIDER, Map.of(ReferenceBioformKind.RAIDER, 6.0d, ReferenceBioformKind.BREAKER, 3.0d),
                ReferenceFormationPhase.MAIN_ACTION, 1.0d, null, settlement.x(), settlement.y(), false);

        world.infection().swarms.add(swarm);
        assertTrue(world.infection().advanceBioforms(world).isEmpty());
        assertEquals(.7d - (3.0d * .0055d + 60.0d * .0015d), control.cordonStrength());
        assertEquals(1, v2.sectorEngagements().size());
        assertEquals("counterattack", v2.sectorEngagements().getFirst().kind());
        assertTrue(world.infection().swarms().isEmpty());
    }

    @Test
    void failedUnsuppliedGrayboxCampaignReturnsEveryNamedResidentAndRetainsItsTerminalOwnerRecord() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(81L));
        ReferenceSettlement settlement = world.settlements().get(1);
        List<String> deployed = new ArrayList<>(settlement.deployPeople(1_000_001,
                Map.of(ReferenceHumanUnitKind.LINE.id(), 3)).get(ReferenceHumanUnitKind.LINE.id()));
        String target = "0:0";
        for (int y = 0; y < world.config().height(); y++) for (int x = 0; x < world.config().width(); x++) {
            world.infection().infectionAt(x, y, .9d);
        }
        world.v2().refreshTerritory(world);
        ReferenceFrontCampaign campaign = campaign(1, settlement.id(), target, ReferenceFrontPhase.ESTABLISH,
                Map.of(settlement.id(), (double) deployed.size()), Map.of(settlement.id(), deployed),
                Map.of(settlement.id(), Map.of(ReferenceHumanUnitKind.LINE, (double) deployed.size())));
        world.v2().mutableFrontCampaigns().put(campaign.id(), campaign);

        world.v2().advanceFrontier(world);

        ReferenceFrontCampaign terminal = world.v2().frontCampaigns().get(campaign.id());
        assertEquals(ReferenceFrontPhase.FAILED, terminal.phase());
        assertEquals(ReferenceFrontPhase.FAILED, terminal.terminalOutcome());
        assertTrue(terminal.personnelBySettlement().isEmpty());
        assertTrue(terminal.residentIdsBySettlement().isEmpty());
        assertEquals(0, settlement.mobilizedPersonnel());
        assertTrue(deployed.stream().allMatch(id -> settlement.residents().resident(id).available()));
        assertTrue(world.events().stream().anyMatch(event -> event.contains("front campaign 1 failed: cannot establish a supplied post")));
    }

    private static ReferenceWorld sourceWorld() {
        return new ReferenceWorld(new ReferenceWorldConfig(64, 44, 12, 41L, 0, true, ReferenceSimulationProfile.SOURCE_V2));
    }

    private static ReferenceFrontCampaign campaign(int id, int leader, String target, ReferenceFrontPhase phase,
                                                   Map<Integer, Double> people, Map<Integer, List<String>> residents,
                                                   Map<Integer, Map<ReferenceHumanUnitKind, Double>> composition) {
        return new ReferenceFrontCampaign(id, ReferenceFrontCampaignKind.CORDON, leader, List.of(leader), target, 0,
                phase, people, residents, composition, "test frontier campaign");
    }
}
