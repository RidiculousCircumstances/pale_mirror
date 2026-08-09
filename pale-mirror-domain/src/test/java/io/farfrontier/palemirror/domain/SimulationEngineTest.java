package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimulationEngineTest {
    @Test
    void infectionRecoveryAndNarrativeAreDeterministic() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        state.putFacility(new FacilityState(mine, 80, 10, 10));
        DomainServices services = new DomainServices();

        DomainEvent infection = services.commands().execute(state, new DomainCommand.AdvanceSimulation(1)).getFirst();
        assertEquals(DomainEventType.MINE_INFECTED, infection.type());
        assertEquals(FacilityStatus.INFECTED, state.facility(mine).orElseThrow().status());

        services.commands().execute(state, new DomainCommand.OfferScenario(infection, StoryAudienceId.globalTestAudience(),
                new ScenarioDefinitionRef("pale_mirror:investigation_recovery", "test",
                        java.util.List.of("OFFERED", "INVESTIGATE", "RECOVER", "RESOLVED"), java.util.List.of(), 0)));
        ScenarioInstance scenario = state.scenarios().stream().findFirst().orElseThrow();
        services.commands().execute(state, new DomainCommand.AcceptScenario(scenario.id()));
        services.commands().execute(state, new DomainCommand.PlayerEnteredFacility(StoryAudienceId.globalTestAudience(), mine));
        services.commands().execute(state, new DomainCommand.ThreatControllerDestroyed(mine, "test:controller"));
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));

        assertEquals(FacilityStatus.OPERATIONAL, state.facility(mine).orElseThrow().status());
        assertEquals(80, state.facility(mine).orElseThrow().currentProduction());
        assertEquals(ScenarioStatus.RESOLVED, state.scenario(scenario.id()).orElseThrow().status());
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.FACILITY_OPERATIONAL));
    }

    @Test
    void negativeSimulationStepFailsVisiblyWithoutMutatingState() {
        WorldState state = new WorldState();

        assertThrows(IllegalArgumentException.class, () -> new DomainServices().commands()
                .execute(state, new DomainCommand.AdvanceSimulation(-1)));
        assertEquals(0, state.simulationStep());
    }

    @Test
    void pinnedScenarioDefinitionSurvivesLaterContentChangesAndCapabilitiesCanResume() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        state.putFacility(new FacilityState(mine, 80, 10, 10));
        DomainServices services = new DomainServices();
        DomainEvent infection = services.commands().execute(state, new DomainCommand.AdvanceSimulation(1)).getFirst();
        ScenarioDefinitionRef pinned = new ScenarioDefinitionRef("pale_mirror:investigation_recovery", "17",
                java.util.List.of("OFFERED", "INVESTIGATE", "RECOVER", "RESOLVED"),
                java.util.List.of("PM_ANCHOR_MATERIALIZATION"), 2, "pale_mirror:crimson_guards", "1");

        services.commands().execute(state, new DomainCommand.OfferScenario(infection, StoryAudienceId.globalTestAudience(), pinned));
        ScenarioInstance scenario = state.scenarios().stream().findFirst().orElseThrow();
        services.commands().execute(state, new DomainCommand.SetScenarioBlocked(scenario.id(), true, "adapter absent"));
        services.commands().execute(state, new DomainCommand.SetScenarioBlocked(scenario.id(), false, "adapter restored"));

        assertEquals("17", scenario.definitionVersion());
        assertEquals(java.util.List.of("PM_ANCHOR_MATERIALIZATION"), scenario.requiredCapabilities());
        assertEquals("pale_mirror:crimson_guards", scenario.encounterProfileId());
        assertEquals("1", scenario.encounterProfileVersion());
        assertEquals(ScenarioStatus.OFFERED, scenario.status());
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.SCENARIO_BLOCKED));
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.SCENARIO_RESUMED));
    }

    @Test
    void noScenarioIsCausallyRecordedOnce() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        state.putFacility(new FacilityState(mine, 80, 10, 10));
        DomainServices services = new DomainServices();
        DomainEvent infection = services.commands().execute(state, new DomainCommand.AdvanceSimulation(1)).getFirst();

        assertEquals(1, services.commands().execute(state,
                new DomainCommand.NoScenario(infection, StoryAudienceId.globalTestAudience(), "capability unavailable")).size());
        assertTrue(services.commands().execute(state,
                new DomainCommand.NoScenario(infection, StoryAudienceId.globalTestAudience(), "capability unavailable")).isEmpty());
    }

    @Test
    void infectedMineDisruptsSettlementSupplyAndRecoveryRestoresIt() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        WorldObjectId settlement = new WorldObjectId("pale_mirror:test_settlement");
        state.putFacility(new FacilityState(mine, 80, 10, 10));
        state.putSettlement(new SettlementState(settlement, mine, 80, 40));
        DomainServices services = new DomainServices();

        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));
        SettlementState disrupted = state.settlements().stream().findFirst().orElseThrow();
        assertTrue(disrupted.supplyDisrupted());
        assertEquals(0, disrupted.currentIronSupply());
        assertEquals(30, disrupted.currentDefense());
        services.commands().execute(state, new DomainCommand.ThreatControllerDestroyed(mine, "test:controller"));
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));

        assertTrue(!disrupted.supplyDisrupted());
        assertEquals(80, disrupted.currentIronSupply());
        assertEquals(40, disrupted.currentDefense());
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.SETTLEMENT_SUPPLY_DISRUPTED));
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.SETTLEMENT_SUPPLY_RESTORED));
    }

    @Test
    void threatTiersAdvanceOnlyFromDeterministicSimulationTime() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        state.putFacility(new FacilityState(mine, 80, 10, 10));
        DomainServices services = new DomainServices();

        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));
        FacilityState facility = state.facility(mine).orElseThrow();
        assertEquals(ThreatTier.FOOTHOLD, facility.threatTier());
        assertEquals(1, facility.threatStartedAtStep());
        assertEquals(1, facility.desiredRevision());

        services.commands().execute(state, new DomainCommand.AdvanceSimulation(11));
        assertEquals(ThreatTier.FOOTHOLD, facility.threatTier());
        assertEquals(1, facility.desiredRevision());
        assertTrue(services.commands().execute(state, new DomainCommand.AdvanceSimulation(1)).stream()
                .anyMatch(event -> event.type() == DomainEventType.THREAT_TIER_ESCALATED));
        assertEquals(ThreatTier.INFESTED, facility.threatTier());
        assertEquals(2, facility.desiredRevision());

        services.commands().execute(state, new DomainCommand.AdvanceSimulation(24));
        assertEquals(ThreatTier.SIEGE, facility.threatTier());
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(36));
        assertEquals(ThreatTier.APEX, facility.threatTier());
        assertEquals(4, facility.desiredRevision());
    }

    @Test
    void siegeGatesRejectPrematureControllerDeathAndAdvanceInOrder() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        FacilityState facility = new FacilityState(mine, 80, 10, 10);
        state.putFacility(facility);
        DomainServices services = new DomainServices();
        facility.infect(0);
        ThreatTierPolicy policy = new ThreatTierPolicy(1, 2, 3);
        facility.advanceThreatTier(100, policy);
        facility.advanceThreatTier(100, policy);
        facility.advanceThreatTier(100, policy);
        assertEquals(SiegeStage.PENDING, facility.siege().stage());

        services.commands().execute(state, new DomainCommand.ActivateSiege(mine, "pale_mirror:crimson_apex",
                "1", "pale_mirror:juggernaut", "test:activate"));
        assertEquals(SiegeStage.NODES, facility.siege().stage());
        assertTrue(services.commands().execute(state, new DomainCommand.ThreatControllerDestroyed(mine, "test:early")).isEmpty());

        for (String node : SiegeState.NODE_SLOTS) {
            services.commands().execute(state, new DomainCommand.SiegeGateDestroyed(mine, node, "test:" + node));
        }
        assertEquals(SiegeStage.BOSS, facility.siege().stage());
        services.commands().execute(state, new DomainCommand.SiegeGateDestroyed(mine, "boss", "test:boss"));
        services.commands().execute(state, new DomainCommand.SiegeGateDestroyed(mine, "bloodlink_i", "test:one"));
        services.commands().execute(state, new DomainCommand.SiegeGateDestroyed(mine, "bloodlink_ii", "test:two"));
        services.commands().execute(state, new DomainCommand.SiegeGateDestroyed(mine, "bloodlink_iii", "test:three"));
        assertEquals(SiegeStage.CONTROLLER_VULNERABLE, facility.siege().stage());
        assertEquals(FacilityStatus.INFECTED, state.facility(mine).orElseThrow().status(), "controller must still exist before observation");
        services.commands().execute(state, new DomainCommand.ThreatControllerDestroyed(mine, "test:controller"));
        assertEquals(FacilityStatus.RECOVERING, state.facility(mine).orElseThrow().status());
    }
}
