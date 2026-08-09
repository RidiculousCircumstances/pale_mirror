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
}
