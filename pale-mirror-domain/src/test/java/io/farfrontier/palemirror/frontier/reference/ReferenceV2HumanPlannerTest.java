package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceV2HumanPlannerTest {
    @Test
    void sourceKnownChrysalisCreatesThePinnedCharterAndAuthorisesItsFirstFront() {
        ReferenceWorld world = sourceWorld();
        ReferenceV2State v2 = world.v2();
        ReferenceSettlement leader = world.settlements().get(1);
        world.day(1);
        leader.facilities().armory(1.0d);
        world.settlements().get(2).facilities().armory(1.0d);
        ReferenceV2OperationalSector target = v2.sectors().get("0:0");
        v2.humanPerceptions().get(leader.id()).belief(new ReferenceV2Belief(target.key(), world.day(), .91d,
                ReferenceObservationSource.SCOUT, .42d, target.organicMass(), .30d, 1.25d, true));
        v2.sectorControl().get(target.key()).state(ReferenceSectorControlState.HIVE);
        leader.add(ReferenceResource.AMMO, 100.0d);

        v2.planHumans(world);

        ReferenceV2DecisionReceipt decision = v2.decisionHistory().stream()
                .filter(item -> item.settlementId() == leader.id()).reduce((first, second) -> second).orElseThrow();
        assertEquals("interrupt_chrysalis", decision.action());
        assertEquals(.57d, decision.risk());
        assertEquals("visible neural chrysalis is a time-critical target", decision.reason());
        assertEquals("—", decision.blockers());
        ReferenceCoalitionCharter charter = v2.charters().get(1);
        assertEquals(List.of(1, 7), charter.members());
        assertEquals("0:0", charter.target());
        assertEquals(25, charter.expiresDay());
        assertEquals("active", charter.status());
        assertEquals(22.259029656923587d, charter.contribution().get(1));
        assertEquals(25.242310452532422d, charter.contribution().get(7));
        assertEquals(55.533082995571334d, charter.compensationDue().get(7));
        ReferenceFrontCampaign campaign = v2.frontCampaigns().get(1);
        assertEquals(ReferenceFrontPhase.RECON, campaign.phase());
        assertEquals(List.of(1, 7), campaign.contributors());
        assertEquals("0:0", campaign.targetSector());
        assertEquals(22.259029656923587d, campaign.personnelBySettlement().get(1));
        assertEquals(25.242310452532422d, campaign.personnelBySettlement().get(7));
        assertEquals(List.of("D1: cities 1,7 signed chrysalis charter 1",
                "D1: Ashfield-01 authorised frontier campaign 1 for sector 0:0"), world.events().subList(world.events().size() - 2, world.events().size()));
    }

    @Test
    void expiredChartersAndDecisionAuditAreBoundedWithoutLeavingPoliticalAuthorityBehind() {
        ReferenceWorld world = sourceWorld();
        ReferenceV2State v2 = world.v2();
        world.day(50);
        for (int id = 1; id <= 130; id++) {
            ReferenceCoalitionCharter charter = new ReferenceCoalitionCharter(id, 1, List.of(1, 7), "0:0", 1, 49,
                    Map.of(1, 1.0d, 7, 1.0d), Map.of(), Map.of());
            charter.status("active");
            charter.reason("test expiry");
            v2.mutableCharters().put(id, charter);
        }

        v2.updateCivics(world);
        for (int day = 1; day <= 257; day++) {
            v2.recordDecision(new ReferenceV2DecisionReceipt(day, 1, "recover", .0d, "test retention", "—"));
        }

        assertTrue(v2.charters().isEmpty());
        assertEquals(128, v2.terminalCharters().size());
        assertEquals(3, v2.terminalCharters().getFirst().id());
        assertEquals(130, v2.terminalCharters().getLast().id());
        assertEquals("expired", v2.terminalCharters().getLast().status());
        assertEquals(256, v2.decisionHistory().size());
        assertEquals(2, v2.decisionHistory().getFirst().day());
        assertEquals(257, v2.decisionHistory().getLast().day());
        assertFalse(v2.charters().values().stream().anyMatch(item -> item.status().equals("active")));
    }

    @Test
    void localQuarantinePropagatesOnlyToItsRoutesAndPoliticalTrustCanRefuseAChrysalisCharter() {
        ReferenceWorld quarantineWorld = sourceWorld();
        ReferenceV2State quarantine = quarantineWorld.v2();
        ReferenceSettlement leader = quarantineWorld.settlements().get(1);
        quarantineWorld.day(1);
        ReferenceV2OperationalSector target = quarantine.sectors().get("0:0");
        quarantine.humanPerceptions().get(leader.id()).belief(new ReferenceV2Belief(target.key(), 1, .8d,
                ReferenceObservationSource.SCOUT, .40d, target.organicMass(), .10d, .0d, false));
        quarantine.civics().get(leader.id()).quarantineUntil(9);

        quarantine.planHumans(quarantineWorld);

        ReferenceV2DecisionReceipt quarantineDecision = quarantine.decisionHistory().stream()
                .filter(item -> item.settlementId() == leader.id()).findFirst().orElseThrow();
        assertEquals("quarantine_and_contain", quarantineDecision.action());
        assertTrue(quarantineWorld.trade().routes().stream().filter(route -> route.a() == leader.id() || route.b() == leader.id())
                .allMatch(route -> route.quarantineUntil() == 9));
        assertTrue(quarantineWorld.trade().routes().stream().filter(route -> route.a() != leader.id() && route.b() != leader.id())
                .allMatch(route -> route.quarantineUntil() < 9));

        ReferenceWorld refusalWorld = sourceWorld();
        ReferenceV2State refusal = refusalWorld.v2();
        ReferenceSettlement refusalLeader = refusalWorld.settlements().get(1);
        refusalWorld.day(1);
        refusalLeader.facilities().armory(1.0d);
        for (ReferenceSettlementDoctrine doctrine : refusal.doctrines().values()) doctrine.solidarity(.0d);
        refusal.doctrines().get(refusalLeader.id()).solidarity(.9d);
        refusal.humanPerceptions().get(refusalLeader.id()).belief(new ReferenceV2Belief("0:0", 1, .9d,
                ReferenceObservationSource.SCOUT, .42d, 0.0d, .30d, 1.25d, true));

        refusal.planHumans(refusalWorld);

        assertTrue(refusal.decisionHistory().stream().anyMatch(item -> item.settlementId() == refusalLeader.id()
                && item.action().equals("interrupt_chrysalis")));
        assertTrue(refusal.charters().isEmpty());
        assertEquals(List.of(refusalLeader.id()), refusal.frontCampaigns().get(1).contributors());
    }

    @Test
    void sourceDecisionPriorityCoversSiegeReconnaissanceAndRecovery() {
        ReferenceWorld holdWorld = sourceWorld();
        holdWorld.day(1);
        ReferenceSettlement holdLeader = holdWorld.settlements().get(1);
        holdLeader.threat(.80d);
        holdWorld.v2().civics().get(holdLeader.id()).state(ReferenceCivicState.SIEGE);
        holdWorld.v2().humanPerceptions().get(holdLeader.id()).belief(new ReferenceV2Belief("0:0", 1, .9d,
                ReferenceObservationSource.SCOUT, .42d, .0d, .30d, 1.25d, true));
        holdWorld.v2().planHumans(holdWorld);
        assertEquals("hold", leaderDecision(holdWorld).action());
        assertEquals(1.0d, leaderDecision(holdWorld).risk());

        ReferenceWorld reconWorld = sourceWorld();
        reconWorld.day(1);
        ReferenceSettlement reconLeader = reconWorld.settlements().get(1);
        reconWorld.v2().humanPerceptions().get(reconLeader.id()).belief(new ReferenceV2Belief("0:0", 1, .8d,
                ReferenceObservationSource.SCOUT, .20d, .0d, .0d, .0d, false));
        reconWorld.v2().planHumans(reconWorld);
        assertEquals("recon_and_guard", leaderDecision(reconWorld).action());
        assertEquals(.20d, leaderDecision(reconWorld).risk());

        ReferenceWorld recoveryWorld = sourceWorld();
        recoveryWorld.day(1);
        recoveryWorld.settlements().get(1).threat(.30d);
        recoveryWorld.v2().planHumans(recoveryWorld);
        assertEquals("recover", leaderDecision(recoveryWorld).action());
        assertEquals(.12d, leaderDecision(recoveryWorld).risk());
    }

    private static ReferenceWorld sourceWorld() {
        return new ReferenceWorld(new ReferenceWorldConfig(64, 44, 12, 41L, 0, true, ReferenceSimulationProfile.SOURCE_V2));
    }

    private static ReferenceV2DecisionReceipt leaderDecision(ReferenceWorld world) {
        return world.v2().decisionHistory().stream().filter(item -> item.settlementId() == 1).findFirst().orElseThrow();
    }
}
