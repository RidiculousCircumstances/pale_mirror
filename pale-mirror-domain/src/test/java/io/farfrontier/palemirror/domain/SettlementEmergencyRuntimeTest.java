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
        state.setSimulationStep(8);
        services.settlementEmergencies().reconcile(state);
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
        return state;
    }
}
