package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementAuthorityProfileTest {
    private static final WorldObjectId COMMUNITY = new WorldObjectId("pale_mirror:native_community");
    private static final WorldObjectId PLACE = new WorldObjectId("pale_mirror:native_place");

    @Test
    void nativeProfileReconcilesPopulationButRejectsPmRelocation() {
        WorldState state = state();
        DomainServices services = new DomainServices();
        services.commands().execute(state, new DomainCommand.RegisterSettlementAuthorityProfile(
                SettlementAuthorityProfile.nativeReconciled(COMMUNITY)));

        var populationEvents = services.commands().execute(state, new DomainCommand.ReconcileSettlementPopulation(
                COMMUNITY, "pale_mirror:native_residents",
                Map.of(SettlementCohort.CIVILIANS, 7, SettlementCohort.GUARDS, 2),
                "native:snapshot:1", "adapter:settlement"));
        assertEquals(9, state.population(COMMUNITY));
        assertTrue(populationEvents.stream().anyMatch(event -> event.type() == DomainEventType.SETTLEMENT_POPULATION_RECONCILED));

        services.settlementEmergencies().reconcile(state);
        assertTrue(state.emergencyWindow(COMMUNITY).isEmpty(), "native population cannot enter PM evacuation policy");
        assertTrue(services.commands().execute(state, new DomainCommand.BeginSettlementEvacuation(COMMUNITY,
                StoryAudienceId.globalTestAudience(), "player:test")).isEmpty());
        assertEquals(PopulationDisposition.RESIDENT, state.populationGroup("pale_mirror:native_residents").orElseThrow().disposition());

        services.commands().execute(state, new DomainCommand.ReconcileSettlementPopulation(COMMUNITY,
                "pale_mirror:native_residents", Map.of(), "native:snapshot:2", "adapter:settlement"));
        assertEquals(OccupancyState.EMPTY, state.place(PLACE).orElseThrow().occupancy(),
                "confirmed native records may reconcile an empty place without PM deciding an evacuation");
    }

    @Test
    void pmOwnedPopulationCannotBeOverwrittenByNativeObservation() {
        WorldState state = state();
        DomainServices services = new DomainServices();
        services.commands().execute(state, new DomainCommand.RegisterSettlementAuthorityProfile(
                SettlementAuthorityProfile.pmManaged(COMMUNITY)));
        assertTrue(services.commands().execute(state, new DomainCommand.ReconcileSettlementPopulation(
                COMMUNITY, "pale_mirror:native_residents", Map.of(SettlementCohort.CIVILIANS, 1),
                "foreign:snapshot", "adapter:settlement")).isEmpty());
        assertEquals(12, state.population(COMMUNITY));
    }

    @Test
    void confirmedManagedResidentDeathChangesOnlyPmOwnedPopulation() {
        WorldState state = state();
        DomainServices services = new DomainServices();
        services.commands().execute(state, new DomainCommand.RegisterSettlementAuthorityProfile(
                SettlementAuthorityProfile.pmManaged(COMMUNITY)));
        var events = services.commands().execute(state, new DomainCommand.ConfirmSettlementResidentDeath(
                COMMUNITY, "pale_mirror:native_residents", SettlementCohort.GUARDS,
                "resident-7", "death:resident-7"));
        assertEquals(11, state.population(COMMUNITY));
        assertEquals(1, state.populationGroup("pale_mirror:native_residents").orElseThrow()
                .cohorts().get(SettlementCohort.GUARDS));
        assertTrue(events.stream().anyMatch(event -> event.type() == DomainEventType.SETTLEMENT_RESIDENT_DEATH_CONFIRMED));

        WorldState nativeState = state();
        services.commands().execute(nativeState, new DomainCommand.RegisterSettlementAuthorityProfile(
                SettlementAuthorityProfile.nativeReconciled(COMMUNITY)));
        assertTrue(services.commands().execute(nativeState, new DomainCommand.ConfirmSettlementResidentDeath(
                COMMUNITY, "pale_mirror:native_residents", SettlementCohort.GUARDS,
                "resident-7", "death:resident-7")).isEmpty());
        assertEquals(12, nativeState.population(COMMUNITY));
    }

    private static WorldState state() {
        WorldState state = new WorldState();
        SettlementCommunity community = new SettlementCommunity(COMMUNITY);
        community.setCrisisState(CrisisState.CRITICAL);
        state.putCommunity(community);
        state.putPlace(new SettlementPlace(PLACE));
        state.putCommunityPlaceBinding(new CommunityPlaceBinding(COMMUNITY, PLACE));
        state.putPopulationGroup(PopulationGroup.residents("pale_mirror:native_residents", COMMUNITY, PLACE,
                Map.of(SettlementCohort.CIVILIANS, 10, SettlementCohort.GUARDS, 2)));
        state.putSecurity(new SettlementSecurity(COMMUNITY, 20));
        state.putSettlementPolicy(new SettlementPolicy(COMMUNITY, 10, 5, 8, 2));
        FacilityState mine = new FacilityState(new WorldObjectId("pale_mirror:native_mine"),
                new InfectionSourceId("pale_mirror:test_source"), 18, 99, 0);
        mine.infect(0);
        state.putFacility(mine);
        state.putLivingRegion(new LivingRegionState("native-test", COMMUNITY, PLACE, mine.id(),
                new WorldObjectId("pale_mirror:alternate"), new WorldObjectId("pale_mirror:route"),
                new WorldObjectId("pale_mirror:alternate_route"), 0, StoryAudienceId.globalTestAudience(),
                RecognitionState.RECOGNIZED, 0));
        return state;
    }
}
