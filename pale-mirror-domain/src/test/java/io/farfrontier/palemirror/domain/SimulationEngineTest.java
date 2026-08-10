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
        DomainServices services = new DomainServices();
        LivingRegionState region = registerLivingRegion(state, services, 0);
        WorldObjectId mine = region.primaryFacilityId();

        services.commands().execute(state, new DomainCommand.TriggerFacilityInfection(mine, "test:infection"));
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));
        ResourceAccount disrupted = state.economy(region.communityId()).orElseThrow().require(ResourceKind.IRON);
        assertEquals(ResourceAvailability.UNAVAILABLE, disrupted.availability());
        assertEquals(0, disrupted.stock());
        assertEquals(47, state.security(region.communityId()).orElseThrow().defenceReadiness());
        services.commands().execute(state, new DomainCommand.ThreatControllerDestroyed(mine, "test:controller"));
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));

        assertEquals(ResourceAvailability.AVAILABLE, disrupted.availability());
        assertEquals(9, disrupted.stock());
        assertEquals(47, state.security(region.communityId()).orElseThrow().defenceReadiness(),
                "defence loss is a durable consequence");
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.SETTLEMENT_SUPPLY_DISRUPTED));
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.SETTLEMENT_SUPPLY_RESTORED));
    }

    @Test
    void validationIsTheOnlyWayAPlannedCreateContractTransfersSupplyAndItAges() {
        WorldState state = new WorldState();
        DomainServices services = new DomainServices();
        LivingRegionState region = registerLivingRegion(state, services, 24);
        state.facility(region.primaryFacilityId()).orElseThrow().infect(0);

        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));
        assertEquals(12, state.economy(region.communityId()).orElseThrow().require(ResourceKind.IRON).stock());
        assertEquals(RouteContractStatus.PLANNED, state.routeContract(region.alternateRouteId()).orElseThrow().status());

        services.commands().execute(state, new DomainCommand.ValidateRouteContract(region.alternateRouteId(), 18,
                state.simulationStep(), "train:one:1", "test:create-train"));
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));
        assertEquals(21, state.economy(region.communityId()).orElseThrow().require(ResourceKind.IRON).stock());
        RouteContract contract = state.routeContract(region.alternateRouteId()).orElseThrow();
        assertEquals(18, contract.transferableCapacity(state.simulationStep()));
        state.setSimulationStep(10);
        assertEquals(9, contract.transferableCapacity(state.simulationStep()));
        state.setSimulationStep(26);
        assertEquals(0, contract.transferableCapacity(state.simulationStep()));
    }

    @Test
    void sharedWorldSiteKeepsAllAffiliationsAndRejectsAmbiguousFlow() {
        WorldState state = new WorldState();
        DomainServices services = new DomainServices();
        LivingRegionState region = registerLivingRegion(state, services, 24);
        RouteContract primary = state.routeContract(region.primaryRouteId()).orElseThrow();
        WorldObjectId secondCommunity = new WorldObjectId("pale_mirror:second_community");

        state.putSiteAffiliation(new SiteAffiliation(primary.destinationEndpoint(), secondCommunity,
                SiteAffiliationRole.RECIPIENT));
        assertEquals(2, state.siteAffiliations(primary.destinationEndpoint(), SiteAffiliationRole.RECIPIENT).size());

        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));

        assertEquals(12, state.economy(region.communityId()).orElseThrow().require(ResourceKind.IRON).stock(),
                "a shared endpoint needs an explicit future beneficiary contract instead of duplicating supply");
    }

    @Test
    void settlementCrisisCanResolveThroughAnObservedAlternateRoute() {
        WorldState state = new WorldState();
        DomainServices services = new DomainServices();
        LivingRegionState region = registerLivingRegion(state, services, 0);

        services.commands().execute(state, new DomainCommand.TriggerFacilityInfection(region.primaryFacilityId(), "test:crisis"));
        DomainEvent crisis = services.commands().execute(state, new DomainCommand.AdvanceSimulation(1)).stream()
                .filter(event -> event.type() == DomainEventType.SETTLEMENT_CRISIS_DETECTED).findFirst().orElseThrow();
        ScenarioDefinitionRef definition = new ScenarioDefinitionRef("pale_mirror:ironhill_supply_crisis", "1",
                java.util.List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED"), java.util.List.of(), 0,
                "", "", ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS);
        services.commands().execute(state, new DomainCommand.OfferScenario(crisis, StoryAudienceId.globalTestAudience(), definition));
        ScenarioInstance scenario = state.scenarios().stream().findFirst().orElseThrow();
        services.commands().execute(state, new DomainCommand.AcceptScenario(scenario.id()));

        services.commands().execute(state, new DomainCommand.ValidateRouteContract(region.alternateRouteId(), 18,
                state.simulationStep(), "train:alternate:1", "test:train"));
        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));

        assertEquals(ScenarioStatus.RESOLVED, scenario.status());
        assertEquals(SettlementCrisisRuntime.ALTERNATE_OUTCOME, scenario.resolutionOutcome());
        assertEquals(RecognitionState.RECOGNIZED, region.recognition());
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.ALTERNATE_SUPPLY_VALIDATED));
    }

    @Test
    void noScenarioDoesNotPauseSettlementPolicy() {
        WorldState state = new WorldState();
        DomainServices services = new DomainServices();
        LivingRegionState region = registerLivingRegion(state, services, 0);
        services.commands().execute(state, new DomainCommand.TriggerFacilityInfection(region.primaryFacilityId(), "test:crisis"));
        DomainEvent crisis = services.commands().execute(state, new DomainCommand.AdvanceSimulation(1)).stream()
                .filter(event -> event.type() == DomainEventType.SETTLEMENT_CRISIS_DETECTED).findFirst().orElseThrow();
        services.commands().execute(state, new DomainCommand.NoScenario(crisis, StoryAudienceId.globalTestAudience(), "pacing"));
        int defence = state.security(region.communityId()).orElseThrow().defenceReadiness();

        services.commands().execute(state, new DomainCommand.AdvanceSimulation(1));

        assertTrue(state.security(region.communityId()).orElseThrow().defenceReadiness() < defence);
        assertEquals(CrisisState.CRITICAL, state.community(region.communityId()).orElseThrow().crisisState());
    }

    @Test
    void typedPlaceEvidenceUpdatesGuardCapabilityWithoutChangingMacroPopulation() {
        WorldState state = new WorldState();
        DomainServices services = new DomainServices();
        LivingRegionState region = registerLivingRegion(state, services, 24);
        int population = state.population(region.communityId());

        assertEquals(2, services.commands().execute(state, new DomainCommand.ObserveSettlementPlace(region.placeId(),
                ObservationFreshness.CURRENT, EvidenceReliability.CONFIRMED, 0,
                "death:guard-0", "player:guard-death")).size());
        assertTrue(services.commands().execute(state, new DomainCommand.ObserveSettlementPlace(region.placeId(),
                ObservationFreshness.CURRENT, EvidenceReliability.CONFIRMED, 0,
                "death:guard-0", "player:guard-death")).isEmpty());
        assertEquals(GuardCapability.ABSENT, state.security(region.communityId()).orElseThrow().guardCapability());
        assertEquals(population, state.population(region.communityId()));
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

    private static LivingRegionState registerLivingRegion(WorldState state, DomainServices services, int ironStock) {
        WorldObjectId communityId = new WorldObjectId("pale_mirror:ironhill_community");
        WorldObjectId placeId = new WorldObjectId("pale_mirror:ironhill_place");
        WorldObjectId primaryMine = new WorldObjectId("pale_mirror:mine17");
        WorldObjectId alternateMine = new WorldObjectId("pale_mirror:red_valley");
        WorldObjectId primarySite = new WorldObjectId("pale_mirror:mine17_dispatch");
        WorldObjectId alternateSite = new WorldObjectId("pale_mirror:red_valley_dispatch");
        WorldObjectId receivingSite = new WorldObjectId("pale_mirror:ironhill_receiving");
        WorldObjectId primaryRoute = new WorldObjectId("pale_mirror:mine17_route");
        WorldObjectId alternateRoute = new WorldObjectId("pale_mirror:red_valley_route");
        LivingRegionState region = new LivingRegionState("pale_mirror:ironhill_v2", communityId, placeId, primaryMine,
                alternateMine, primaryRoute, alternateRoute, 0, StoryAudienceId.globalTestAudience(),
                RecognitionState.RECOGNIZED, 0);
        services.commands().execute(state, new DomainCommand.RegisterLivingRegion(region,
                java.util.List.of(new FacilityState(primaryMine, TEST_SOURCE, 18, 99, 0),
                        new FacilityState(alternateMine, TEST_SOURCE, 18, 99, 0)),
                new SettlementCommunity(communityId), new SettlementPlace(placeId),
                new CommunityPlaceBinding(communityId, placeId),
                new SettlementEconomy(communityId, java.util.Map.of(ResourceKind.IRON,
                        new ResourceAccount(96, ironStock, 0, 12, 9))),
                new SettlementSecurity(communityId, 55), new SettlementPolicy(communityId, 10, 5, 8, 2),
                java.util.List.of(new WorldSite(primarySite, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                        new WorldSite(alternateSite, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                        new WorldSite(receivingSite, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL)),
                java.util.List.of(new SiteAffiliation(primarySite, primaryMine, SiteAffiliationRole.SUPPLIER),
                        new SiteAffiliation(alternateSite, alternateMine, SiteAffiliationRole.SUPPLIER),
                        new SiteAffiliation(receivingSite, communityId, SiteAffiliationRole.RECIPIENT)),
                java.util.List.of(new SiteCapability(primarySite, SiteCapabilityType.LOGISTICS, ResourceKind.IRON, 18),
                        new SiteCapability(alternateSite, SiteCapabilityType.LOGISTICS, ResourceKind.IRON, 18),
                        new SiteCapability(receivingSite, SiteCapabilityType.LOGISTICS, ResourceKind.IRON, 18)),
                java.util.List.of(new RouteContract(primaryRoute, primarySite, receivingSite, RouteProvider.PALE_MIRROR,
                                ResourceKind.IRON, 18, 8, 24, RouteContractStatus.PLANNED),
                        new RouteContract(alternateRoute, alternateSite, receivingSite, RouteProvider.CREATE,
                        ResourceKind.IRON, 18, 8, 24, RouteContractStatus.PLANNED)),
                java.util.List.of(PopulationGroup.residents("pale_mirror:test_residents", communityId, placeId,
                        java.util.Map.of(SettlementCohort.CIVILIANS, 78, SettlementCohort.GUARDS, 2)))));
        return region;
    }
}
