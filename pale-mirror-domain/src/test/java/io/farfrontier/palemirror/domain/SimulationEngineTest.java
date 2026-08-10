package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimulationEngineTest {
    private static final InfectionSourceId TEST_SOURCE = new InfectionSourceId("pale_mirror:test_source");
    @Test
    void infectionRecoveryAndNarrativeAreDeterministic() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        state.putFacility(new FacilityState(mine, TEST_SOURCE, 80, 10, 10));
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
        state.putFacility(new FacilityState(mine, TEST_SOURCE, 80, 10, 10));
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
        state.putFacility(new FacilityState(mine, TEST_SOURCE, 80, 10, 10));
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
        WorldObjectId route = new WorldObjectId("pale_mirror:test_route");
        state.putFacility(new FacilityState(mine, TEST_SOURCE, 80, 10, 10));
        state.putSettlement(ironhill(settlement, 80, 40, 0));
        state.putRoute(new RouteState(route, mine, settlement, ResourceKind.IRON, 80, 80, RouteStatus.OPERATIONAL));
        DomainServices services = new DomainServices();

        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));
        SettlementState disrupted = state.settlements().stream().findFirst().orElseThrow();
        assertTrue(disrupted.supplyDisrupted());
        assertEquals(0, disrupted.stock(ResourceKind.IRON));
        assertEquals(32, disrupted.currentDefense());
        services.commands().execute(state, new DomainCommand.ThreatControllerDestroyed(mine, "test:controller"));
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));

        assertTrue(!disrupted.supplyDisrupted());
        assertEquals(68, disrupted.stock(ResourceKind.IRON));
        assertEquals(40, disrupted.currentDefense());
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.SETTLEMENT_SUPPLY_DISRUPTED));
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.SETTLEMENT_SUPPLY_RESTORED));
    }

    @Test
    void observedRouteCapacityIsTheOnlyWayAPlannedRouteTransfersSupply() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        WorldObjectId settlement = new WorldObjectId("pale_mirror:test_settlement");
        WorldObjectId route = new WorldObjectId("pale_mirror:test_route");
        state.putFacility(new FacilityState(mine, TEST_SOURCE, 18, 99, 0));
        state.putSettlement(ironhill(settlement, 80, 55, 24));
        state.putRoute(new RouteState(route, mine, settlement, ResourceKind.IRON, 18, RouteStatus.PLANNED));
        DomainServices services = new DomainServices();

        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));
        assertEquals(12, state.settlement(settlement).orElseThrow().stock(ResourceKind.IRON));
        assertEquals(RouteStatus.PLANNED, state.route(route).orElseThrow().status());

        services.commands().execute(state, new DomainCommand.ObserveRouteCapacity(route, 18, "test:create-train"));
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));
        assertEquals(18, state.settlement(settlement).orElseThrow().stock(ResourceKind.IRON));
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.ROUTE_OPERATIONAL));
    }

    @Test
    void evacuationCreatesOnePersistentMigrantGroupAndDeclinesSettlement() {
        WorldState state = new WorldState();
        WorldObjectId settlement = new WorldObjectId("pale_mirror:ironhill");
        WorldObjectId migrants = new WorldObjectId("pale_mirror:ironhill_refugees");
        state.putSettlement(ironhill(settlement, 80, 55, 48));
        DomainServices services = new DomainServices();

        assertEquals(2, services.commands().execute(state,
                new DomainCommand.EvacuateSettlement(settlement, migrants, 56, "test:evacuate")).size());
        assertEquals(24, state.settlement(settlement).orElseThrow().population());
        assertEquals(SettlementStatus.DECLINING, state.settlement(settlement).orElseThrow().status());
        assertEquals(56, state.migrantGroup(migrants).orElseThrow().population());
        assertTrue(services.commands().execute(state,
                new DomainCommand.EvacuateSettlement(settlement, migrants, 10, "test:duplicate")).isEmpty());
    }

    @Test
    void settlementCrisisCanResolveThroughAnObservedAlternateRoute() {
        WorldState state = new WorldState();
        WorldObjectId primaryMine = new WorldObjectId("pale_mirror:mine17");
        WorldObjectId alternateMine = new WorldObjectId("pale_mirror:red_valley");
        WorldObjectId settlement = new WorldObjectId("pale_mirror:ironhill");
        WorldObjectId primaryRoute = new WorldObjectId("pale_mirror:mine17_route");
        WorldObjectId alternateRoute = new WorldObjectId("pale_mirror:red_valley_route");
        LivingRegionState region = new LivingRegionState("pale_mirror:ironhill_v1", settlement, primaryMine,
                alternateMine, primaryRoute, alternateRoute, 0, StoryAudienceId.globalTestAudience(),
                LivingRegionStatus.DISCOVERED, 0);
        DomainServices services = new DomainServices();
        services.commands().execute(state, new DomainCommand.RegisterLivingRegion(region,
                java.util.List.of(new FacilityState(primaryMine, TEST_SOURCE, 18, 99, 0),
                        new FacilityState(alternateMine, TEST_SOURCE, 18, 99, 0)),
                ironhill(settlement, 80, 55, 0),
                java.util.List.of(new RouteState(primaryRoute, primaryMine, settlement, ResourceKind.IRON, 18, 18,
                                RouteStatus.OPERATIONAL),
                        new RouteState(alternateRoute, alternateMine, settlement, ResourceKind.IRON, 18,
                                RouteStatus.PLANNED))));

        services.commands().execute(state, new DomainCommand.TriggerFacilityInfection(primaryMine, "test:crisis"));
        DomainEvent shortage = services.commands().execute(state, new DomainCommand.AdvanceSimulation(1)).stream()
                .filter(event -> event.type() == DomainEventType.SETTLEMENT_SUPPLY_DISRUPTED).findFirst().orElseThrow();
        ScenarioDefinitionRef definition = new ScenarioDefinitionRef("pale_mirror:ironhill_supply_crisis", "1",
                java.util.List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED"), java.util.List.of(), 0,
                "", "", ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS);
        services.commands().execute(state, new DomainCommand.OfferScenario(shortage, StoryAudienceId.globalTestAudience(), definition));
        ScenarioInstance scenario = state.scenarios().stream().findFirst().orElseThrow();
        services.commands().execute(state, new DomainCommand.AcceptScenario(scenario.id()));

        services.commands().execute(state, new DomainCommand.ObserveRouteCapacity(alternateRoute, 18, "test:train"));
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));

        assertEquals(ScenarioStatus.RESOLVED, scenario.status());
        assertEquals(LivingRegionStatus.RESOLVED, region.status());
        assertEquals(SettlementStatus.STABLE, state.settlement(settlement).orElseThrow().status());
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.ROUTE_OPERATIONAL));
    }

    @Test
    void threatTiersAdvanceOnlyFromDeterministicSimulationTime() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        state.putFacility(new FacilityState(mine, TEST_SOURCE, 80, 10, 10));
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
        FacilityState facility = new FacilityState(mine, TEST_SOURCE, 80, 10, 10);
        state.putFacility(facility);
        DomainServices services = new DomainServices();
        facility.infect(0);
        ThreatTierPolicy policy = new ThreatTierPolicy(1, 2, 3);
        facility.advanceThreatTier(100, policy);
        facility.advanceThreatTier(100, policy);
        facility.advanceThreatTier(100, policy);
        assertEquals(SourceGateStatus.PENDING, facility.gate().status());

        GatePlanRef plan = new GatePlanRef("pale_mirror:test_gate", "1", java.util.List.of(
                new GatePhaseRef("first", java.util.List.of("one", "two")),
                new GatePhaseRef("second", java.util.List.of("three"))));
        services.commands().execute(state, new DomainCommand.ActivateGate(mine, plan, "test:activate"));
        assertEquals(SourceGateStatus.ACTIVE, facility.gate().status());
        assertTrue(services.commands().execute(state, new DomainCommand.ThreatControllerDestroyed(mine, "test:early")).isEmpty());

        for (String part : java.util.List.of("one", "two")) {
            services.commands().execute(state, new DomainCommand.GatePartDestroyed(mine, part, "test:" + part));
        }
        assertEquals("second", facility.gate().currentPhase().orElseThrow().id());
        services.commands().execute(state, new DomainCommand.GatePartDestroyed(mine, "three", "test:three"));
        assertEquals(SourceGateStatus.UNSEALED, facility.gate().status());
        assertEquals(FacilityStatus.INFECTED, state.facility(mine).orElseThrow().status(), "controller must still exist before observation");
        services.commands().execute(state, new DomainCommand.ThreatControllerDestroyed(mine, "test:controller"));
        assertEquals(FacilityStatus.RECOVERING, state.facility(mine).orElseThrow().status());
    }

    private static SettlementState ironhill(WorldObjectId id, int population, int defense, int ironStock) {
        return new SettlementState(id, population, defense,
                java.util.Map.of(ResourceKind.IRON, new ResourceStock(96, ironStock)),
                java.util.Map.of(ResourceKind.IRON, 12));
    }
}
