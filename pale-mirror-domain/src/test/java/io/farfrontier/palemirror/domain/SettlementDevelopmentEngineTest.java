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
        assertEquals(0, iron.reserved());
        assertEquals(24, intent.remainingAmount(), "the offer opens a project escrow instead of silently taxing stock");

        services.commands().execute(state, new DomainCommand.ContributeDevelopmentIntent(intent.id(), 24, "player:iron"));
        assertTrue(services.commands().execute(state,
                new DomainCommand.ContributeDevelopmentIntent(intent.id(), 24, "player:iron")).isEmpty(),
                "a persisted receipt must not fund the project twice");
        services.commands().execute(state, new DomainCommand.StartDevelopmentIntent(intent.id()));
        services.commands().execute(state, new DomainCommand.CompleteDevelopmentIntent(intent.id()));
        assertEquals(200, iron.capacity());
        assertEquals(90, iron.stock(), "player project iron never passes through settlement stock");
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
        while (!intent.funded()) services.settlementDevelopment().reconcile(state);

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
        group.hostAt(SHELTER);
        state.putWorldPath(new WorldPath("pale_mirror:return", "1", SHELTER, HOME_ENDPOINT,
                java.util.List.of(new WorldPathNode("camp", "minecraft:overworld", 100, 64, 0, true),
                        new WorldPathNode("home", "minecraft:overworld", 0, 64, 0, true))));
        state.place(PLACE).orElseThrow().setOccupancy(OccupancyState.EMPTY);
        DomainServices services = new DomainServices();

        services.settlementDevelopment().reconcile(state);
        assertEquals(PopulationDisposition.RESETTLED, group.disposition());
        services.settlementDevelopment().reconcile(state);
        assertEquals(PopulationDisposition.IN_TRANSIT, group.disposition());
        WorldJourney journey = state.journey(group.journeyId()).orElseThrow();
        while (!journey.terminal()) journey.advanceAbstractStep();
        services.settlementDevelopment().reconcile(state);
        assertEquals(PopulationDisposition.RESIDENT, group.disposition());
        assertEquals(OccupancyState.INHABITED, state.place(PLACE).orElseThrow().occupancy());
    }

    @Test
    void nativeAuthorityAllowsExternalStorehouseButNeverPmPopulationGrowth() {
        WorldState state = developmentState();
        state.putSettlementAuthorityProfile(SettlementAuthorityProfile.nativeReconciled(COMMUNITY));
        DomainServices services = new DomainServices();
        for (int step = 0; step < 6; step++) services.settlementDevelopment().reconcile(state);
        DevelopmentIntent intent = state.developmentIntents().stream().findFirst().orElseThrow();
        services.commands().execute(state, new DomainCommand.ContributeDevelopmentIntent(intent.id(), 24, "player:native"));
        services.commands().execute(state, new DomainCommand.StartDevelopmentIntent(intent.id()));
        services.commands().execute(state, new DomainCommand.CompleteDevelopmentIntent(intent.id()));
        services.commands().execute(state, new DomainCommand.DepositResource(COMMUNITY, ResourceKind.IRON, 94, "test:native-restock"));

        for (int step = 0; step < 16; step++) services.settlementDevelopment().reconcile(state);
        assertEquals(80, state.population(COMMUNITY));
        assertEquals(0, state.settlementDevelopment(COMMUNITY).orElseThrow().stableGrowthSteps());
    }

    @Test
    void playerApprovedAlternateDispatchUsesCanonicalEscrowBeforeCommissioning() {
        WorldState state = developmentState();
        WorldObjectId dispatch = new WorldObjectId("pale_mirror:alternate_dispatch");
        WorldObjectId receiving = new WorldObjectId("pale_mirror:alternate_receiving");
        WorldObjectId alternateRoute = new WorldObjectId("pale_mirror:alternate_route");
        state.putSite(new WorldSite(dispatch, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.DEGRADED));
        state.putSite(new WorldSite(receiving, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL));
        state.putSiteAffiliation(new SiteAffiliation(dispatch, COMMUNITY, SiteAffiliationRole.RECIPIENT));
        state.putSiteCapability(new SiteCapability(dispatch, SiteCapabilityType.LOGISTICS, ResourceKind.IRON, 18));
        state.putRouteContract(new RouteContract(alternateRoute, dispatch, receiving, RouteProvider.CREATE,
                ResourceKind.IRON, 18, 8, 24, RouteContractStatus.PLANNED));
        state.putLivingRegion(new LivingRegionState("pale_mirror:development_region", COMMUNITY, PLACE,
                new WorldObjectId("pale_mirror:primary_mine"), new WorldObjectId("pale_mirror:alternate_mine"),
                new WorldObjectId("pale_mirror:primary_route"), alternateRoute, 0, false));
        DomainServices services = new DomainServices();
        StoryAudienceId audience = StoryAudienceId.globalTestAudience();

        assertTrue(services.commands().execute(state, new DomainCommand.ValidateRouteContract(alternateRoute,
                18, 0, "train:before-factory", "test:before-factory")).isEmpty());
        assertTrue(services.commands().execute(state, new DomainCommand.PlanAlternateDispatch(
                COMMUNITY, audience, dispatch, 12, "atlas:without-response")).isEmpty());
        state.putScenario(new ScenarioInstance("pm:scenario:alternate-dispatch", "pm:event:dispatch", COMMUNITY,
                audience, "pale_mirror:settlement_supply_crisis", "1", java.util.List.of("RESPOND"),
                java.util.List.of(), "", "", ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS,
                ScenarioStatus.RESPOND, null, ""));
        assertEquals(2, services.commands().execute(state, new DomainCommand.PlanAlternateDispatch(
                COMMUNITY, audience, dispatch, 12, "atlas:test")).size());
        DevelopmentIntent intent = state.developmentIntents().stream()
                .filter(value -> value.type() == DevelopmentIntentType.COMMISSION_ALTERNATE_DISPATCH)
                .findFirst().orElseThrow();
        assertEquals(12, state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON).reserved());
        assertTrue(services.commands().execute(state, new DomainCommand.PlanAlternateDispatch(
                COMMUNITY, audience, dispatch, 12, "atlas:duplicate")).isEmpty());
        services.commands().execute(state, new DomainCommand.StartDevelopmentIntent(intent.id()));
        services.commands().execute(state, new DomainCommand.CompleteDevelopmentIntent(intent.id()));
        assertEquals(OperationalState.OPERATIONAL, state.site(dispatch).orElseThrow().operationalState());
        assertEquals(1, services.commands().execute(state, new DomainCommand.ValidateRouteContract(alternateRoute,
                18, 0, "train:after-factory", "test:after-factory")).size());
        assertEquals(78, state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON).stock());
        assertEquals(0, state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON).reserved());
    }

    private static final WorldObjectId COMMUNITY = new WorldObjectId("pale_mirror:community");
    private static final WorldObjectId PLACE = new WorldObjectId("pale_mirror:place");
    private static final WorldObjectId STORAGE = new WorldObjectId("pale_mirror:depot");
    private static final WorldObjectId DEVELOPMENT = new WorldObjectId("pale_mirror:development_plot");
    private static final WorldObjectId SHELTER = new WorldObjectId("pale_mirror:shelter");
    private static final WorldObjectId HOME_ENDPOINT = new WorldObjectId("pale_mirror:home_endpoint");

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
        state.putSite(new WorldSite(DEVELOPMENT, WorldSiteType.DEVELOPMENT, OperationalState.DEGRADED));
        state.putSiteAffiliation(new SiteAffiliation(DEVELOPMENT, COMMUNITY, SiteAffiliationRole.RECIPIENT));
        return state;
    }
}
