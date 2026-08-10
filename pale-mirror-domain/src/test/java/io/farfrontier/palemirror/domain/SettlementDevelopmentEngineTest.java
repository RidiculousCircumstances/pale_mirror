package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class SettlementDevelopmentEngineTest {
    @Test
    void surplusPlansStorehouseAndVerifiedCompletionUnlocksGrowth() {
        WorldState state = developmentState();
        DomainServices services = new DomainServices();
        for (int step = 0; step < 6; step++) services.settlementDevelopment().reconcile(state);

        DevelopmentIntent intent = state.developmentIntents().stream().findFirst().orElseThrow();
        ResourceAccount iron = state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON);
        assertEquals(DevelopmentIntentState.PLANNED, intent.state());
        assertEquals(24, iron.reserved());
        assertEquals(90, iron.stock(), "planning reserves rather than consuming investment stock");

        services.commands().execute(state, new DomainCommand.StartDevelopmentIntent(intent.id()));
        services.commands().execute(state, new DomainCommand.CompleteDevelopmentIntent(intent.id()));
        assertEquals(200, iron.capacity());
        assertEquals(66, iron.stock());
        assertEquals(88, state.settlementDevelopment(COMMUNITY).orElseThrow().housingCapacity());
        assertEquals(35, state.settlementDevelopment(COMMUNITY).orElseThrow().prosperity());

        services.commands().execute(state, new DomainCommand.DepositResource(COMMUNITY, ResourceKind.IRON, 94, "test:restock"));
        for (int step = 0; step < 8; step++) services.settlementDevelopment().reconcile(state);
        assertEquals(81, state.population(COMMUNITY));
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.SETTLEMENT_POPULATION_GREW));
    }

    @Test
    void cancelledPhysicalUpgradeReleasesReservationExactlyOnce() {
        WorldState state = developmentState();
        DomainServices services = new DomainServices();
        for (int step = 0; step < 6; step++) services.settlementDevelopment().reconcile(state);
        DevelopmentIntent intent = state.developmentIntents().stream().findFirst().orElseThrow();

        services.commands().execute(state, new DomainCommand.StartDevelopmentIntent(intent.id()));
        services.commands().execute(state, new DomainCommand.CancelDevelopmentIntent(intent.id(), "physical conflict"));
        assertEquals(0, state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON).reserved());
        assertEquals(90, state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON).stock());
        assertTrue(services.commands().execute(state,
                new DomainCommand.CancelDevelopmentIntent(intent.id(), "duplicate")).isEmpty());
    }

    @Test
    void displacedGroupReturnsOnlyToObservedNonRuinedHome() {
        WorldState state = developmentState();
        PopulationGroup group = state.populationGroups(COMMUNITY).getFirst();
        group.beginEvacuation(0);
        group.displace();
        state.place(PLACE).orElseThrow().setOccupancy(OccupancyState.EMPTY);
        DomainServices services = new DomainServices();

        services.settlementDevelopment().reconcile(state);
        assertEquals(PopulationDisposition.DISPLACED, group.disposition());
        services.settlementDevelopment().reconcile(state);
        assertEquals(PopulationDisposition.RESIDENT, group.disposition());
        assertEquals(OccupancyState.INHABITED, state.place(PLACE).orElseThrow().occupancy());
    }

    private static final WorldObjectId COMMUNITY = new WorldObjectId("pale_mirror:community");
    private static final WorldObjectId PLACE = new WorldObjectId("pale_mirror:place");
    private static final WorldObjectId STORAGE = new WorldObjectId("pale_mirror:depot");

    private static WorldState developmentState() {
        WorldState state = new WorldState();
        state.putCommunity(new SettlementCommunity(COMMUNITY));
        state.putPlace(new SettlementPlace(PLACE));
        state.putCommunityPlaceBinding(new CommunityPlaceBinding(COMMUNITY, PLACE));
        state.putPopulationGroup(PopulationGroup.residents("pale_mirror:residents", COMMUNITY, PLACE,
                Map.of(SettlementCohort.CIVILIANS, 80)));
        state.putEconomy(new SettlementEconomy(COMMUNITY, Map.of(ResourceKind.IRON,
                new ResourceAccount(100, 90, 0, 12, 9, 18, 12, 12, 6, ResourceAvailability.AVAILABLE))));
        state.putSecurity(new SettlementSecurity(COMMUNITY, 55));
        state.putSettlementPolicy(new SettlementPolicy(COMMUNITY, 10, 5, 8, 2));
        state.putSettlementDevelopment(new SettlementDevelopment(COMMUNITY, 25, 0, 80, 64, 0));
        state.putDevelopmentPolicy(SettlementDevelopmentPolicy.defaults(COMMUNITY));
        state.putSite(new WorldSite(STORAGE, WorldSiteType.STORAGE, OperationalState.OPERATIONAL));
        state.putSiteAffiliation(new SiteAffiliation(STORAGE, COMMUNITY, SiteAffiliationRole.RECIPIENT));
        state.putSiteCapability(new SiteCapability(STORAGE, SiteCapabilityType.STORAGE, ResourceKind.IRON, 100));
        return state;
    }
}
