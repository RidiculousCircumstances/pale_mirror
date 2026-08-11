package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class SettlementEmergencyRuntimeTest {
    @Test
    void graceWindowExpiresIntoDisplacementWithoutNarrator() {
        WorldState state = emergencyState();
        DomainServices services = new DomainServices();

        assertTrue(services.settlementEmergencies().reconcile(state).stream()
                .anyMatch(value -> value.type() == DomainEventType.SETTLEMENT_EMERGENCY_WINDOW_OPENED));
        reconcileThrough(state, services, 8);
        assertEquals(PopulationDisposition.EVACUATING, state.populationGroups(COMMUNITY).getFirst().disposition());
        assertEquals(OccupancyState.EVACUATING, state.place(PLACE).orElseThrow().occupancy());

        state.setSimulationStep(10);
        services.settlementEmergencies().reconcile(state);
        assertEquals(PopulationDisposition.DISPLACED, state.populationGroups(COMMUNITY).getFirst().disposition());
        assertEquals(OccupancyState.EMPTY, state.place(PLACE).orElseThrow().occupancy());
        assertEquals(StructuralIntegrity.RUINED, state.place(PLACE).orElseThrow().structuralIntegrity());
        assertEquals(80, state.population(COMMUNITY), "evacuation moves people without deleting the community");
    }

    @Test
    void primaryAudienceCanBeginEvacuationDuringWindow() {
        WorldState state = emergencyState();
        DomainServices services = new DomainServices();
        services.settlementEmergencies().reconcile(state);

        assertEquals(1, services.commands().execute(state, new DomainCommand.BeginSettlementEvacuation(COMMUNITY,
                StoryAudienceId.globalTestAudience(), "player:test")).size());
        assertTrue(services.commands().execute(state, new DomainCommand.BeginSettlementEvacuation(COMMUNITY,
                new StoryAudienceId("pm:audience:other"), "player:other")).isEmpty());
    }

    @Test
    void preparedShelterTurnsEvacuationIntoResettlementWithoutMakingItASecondPopulationSource() {
        WorldState state = emergencyState();
        DomainServices services = new DomainServices();
        services.settlementEmergencies().reconcile(state);
        WorldObjectId shelter = new WorldObjectId("pale_mirror:prepared_shelter");

        assertTrue(services.commands().execute(state, new DomainCommand.RegisterEvacuationShelter(COMMUNITY,
                StoryAudienceId.globalTestAudience(), new WorldSite(shelter, WorldSiteType.SHELTER, OperationalState.OPERATIONAL),
                new SiteCapability(shelter, SiteCapabilityType.SHELTER, null, 80), "player:anchor")).stream()
                .anyMatch(event -> event.type() == DomainEventType.REFUGEE_SHELTER_PREPARED));
        services.commands().execute(state, new DomainCommand.BeginSettlementEvacuation(COMMUNITY,
                StoryAudienceId.globalTestAudience(), "player:evacuate"));
        state.setSimulationStep(10);
        services.settlementEmergencies().reconcile(state);

        assertEquals(PopulationDisposition.RESETTLED, state.populationGroups(COMMUNITY).getFirst().disposition());
        assertEquals(80, state.population(COMMUNITY), "the prepared camp hosts the existing group; it does not clone it");
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.POPULATION_RESETTLED));
    }

    @Test
    void shelterBoundToAnotherCommunityCannotReceiveThisEvacuation() {
        WorldState state = emergencyState();
        DomainServices services = new DomainServices();
        WorldObjectId otherCommunity = new WorldObjectId("pale_mirror:other_community");
        WorldObjectId otherShelter = new WorldObjectId("pale_mirror:other_shelter");
        state.putSite(new WorldSite(otherShelter, WorldSiteType.SHELTER, OperationalState.OPERATIONAL));
        state.putSiteAffiliation(new SiteAffiliation(otherShelter, otherCommunity, SiteAffiliationRole.RECIPIENT));
        state.putSiteCapability(new SiteCapability(otherShelter, SiteCapabilityType.SHELTER, null, 80));

        services.settlementEmergencies().reconcile(state);
        reconcileThrough(state, services, 8);
        state.setSimulationStep(10);
        services.settlementEmergencies().reconcile(state);

        assertEquals(PopulationDisposition.DISPLACED, state.populationGroups(COMMUNITY).getFirst().disposition(),
                "a shelter for another community must not silently redirect this population group");
    }

    @Test
    void irreversibleWindowPausesWhileAudienceIsOffline() {
        WorldState state = emergencyState();
        DomainServices services = new DomainServices();
        state.regionAccess(StoryAudienceId.globalTestAudience(), "test").orElseThrow()
                .observe(AudienceRegionReachability.REMOTE, false, 0, "offline");

        services.settlementEmergencies().reconcile(state);
        reconcileThrough(state, services, 20);

        assertEquals(EmergencyWindowState.OPEN, state.emergencyWindow(COMMUNITY).orElseThrow().state());
        assertEquals(16, state.emergencyWindow(COMMUNITY).orElseThrow().remainingGraceSteps());
    }

    @Test
    void remoteAudienceReceivesAReachabilityAdjustedWindow() {
        WorldState state = emergencyState();
        state.regionAccess(StoryAudienceId.globalTestAudience(), "test").orElseThrow()
                .observe(AudienceRegionReachability.REMOTE, true, 0, "remote");

        new DomainServices().settlementEmergencies().reconcile(state);

        assertEquals(16, state.emergencyWindow(COMMUNITY).orElseThrow().remainingGraceSteps());
        assertEquals(AudienceRegionReachability.REMOTE,
                state.emergencyWindow(COMMUNITY).orElseThrow().reachabilityAtOpen());
    }

    private static final WorldObjectId COMMUNITY = new WorldObjectId("pale_mirror:community");
    private static final WorldObjectId PLACE = new WorldObjectId("pale_mirror:place");
    private static final WorldObjectId MINE = new WorldObjectId("pale_mirror:mine");

    private static WorldState emergencyState() {
        WorldState state = new WorldState();
        SettlementCommunity community = new SettlementCommunity(COMMUNITY);
        community.setCrisisState(CrisisState.CRITICAL);
        state.putCommunity(community);
        state.putPlace(new SettlementPlace(PLACE));
        state.putCommunityPlaceBinding(new CommunityPlaceBinding(COMMUNITY, PLACE));
        state.putSecurity(new SettlementSecurity(COMMUNITY, 55, 20, 0, GuardCapability.ABSENT));
        state.putSettlementPolicy(new SettlementPolicy(COMMUNITY, 10, 5, 8, 2));
        state.putPopulationGroup(PopulationGroup.residents("pale_mirror:residents", COMMUNITY, PLACE,
                Map.of(SettlementCohort.CIVILIANS, 70, SettlementCohort.GUARDS, 10)));
        FacilityState mine = new FacilityState(MINE, new InfectionSourceId("pale_mirror:test_source"), 18, 99, 0);
        mine.infect(0);
        state.putFacility(mine);
        state.putLivingRegion(new LivingRegionState("test", COMMUNITY, PLACE, MINE,
                new WorldObjectId("pale_mirror:alternate"), new WorldObjectId("pale_mirror:route"),
                new WorldObjectId("pale_mirror:alternate_route"), 0, StoryAudienceId.globalTestAudience(),
                RecognitionState.RECOGNIZED, 0));
        state.putRegionAccess(new AudienceRegionAccess(StoryAudienceId.globalTestAudience(), "test",
                AudienceRegionReachability.LOCAL, true, 0, "initial"));
        return state;
    }

    private static void reconcileThrough(WorldState state, DomainServices services, long finalStep) {
        for (long step = state.simulationStep() + 1; step <= finalStep; step++) {
            state.setSimulationStep(step);
            services.settlementEmergencies().reconcile(state);
        }
    }
}
