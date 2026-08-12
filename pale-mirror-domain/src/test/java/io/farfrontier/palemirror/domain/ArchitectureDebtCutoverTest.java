package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ArchitectureDebtCutoverTest {
    @Test
    void detailedHistoryCompactsIntoPerSubjectTypeSummary() {
        WorldObjectId subject = new WorldObjectId("pale_mirror:test_subject");
        WorldStateHydration.Builder builder = WorldStateHydration.builder().schemaVersion(36)
                .eventSequence(WorldState.MAX_DETAILED_HISTORY + 7L);
        for (int index = 1; index <= WorldState.MAX_DETAILED_HISTORY + 7; index++) {
            builder.event(new DomainEvent("pm:event:" + index, DomainEventType.MINE_INFECTED,
                    subject, index, "test", "test"));
        }
        WorldState state = builder.build();

        assertEquals(WorldState.MAX_DETAILED_HISTORY, state.history().size());
        assertEquals(7L, state.historySummaries().iterator().next().count());
    }

    @Test
    void validatorRejectsOrphanedAggregateReference() {
        WorldObjectId missingSite = new WorldObjectId("pale_mirror:missing_site");
        WorldObjectId missingObject = new WorldObjectId("pale_mirror:missing_object");
        WorldState state = WorldStateHydration.builder().schemaVersion(36)
                .affiliation(new SiteAffiliation(missingSite, missingObject, SiteAffiliationRole.SUPPLIER))
                .build();

        assertThrows(DomainStateValidationException.class, () -> DomainStateValidator.validate(state, 36));
    }

    @Test
    void hydrationRejectsDuplicateIdentityBeforeMapNormalization() {
        WorldObjectId facilityId = id("duplicate_facility");
        var builder = WorldStateHydration.builder().schemaVersion(36)
                .facility(new FacilityState(facilityId, new InfectionSourceId("test:threat"), 4, 10, 0))
                .facility(new FacilityState(facilityId, new InfectionSourceId("test:threat"), 8, 10, 0));

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void sharedProductionIsAllocatedByDemandWeightNotRouteId() {
        WorldObjectId mine = id("mine");
        WorldObjectId origin = id("origin");
        WorldObjectId destinationA = id("destination_a");
        WorldObjectId destinationB = id("destination_b");
        WorldObjectId communityA = id("community_a");
        WorldObjectId communityB = id("community_b");
        WorldStateHydration.Builder builder = WorldStateHydration.builder().schemaVersion(36)
                .facility(new FacilityState(mine, new InfectionSourceId("test:threat"), 8, 100, 0))
                .site(new WorldSite(origin, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL))
                .site(new WorldSite(destinationA, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL))
                .site(new WorldSite(destinationB, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL))
                .affiliation(new SiteAffiliation(origin, mine, SiteAffiliationRole.SUPPLIER))
                .affiliation(new SiteAffiliation(destinationA, communityA, SiteAffiliationRole.RECIPIENT))
                .affiliation(new SiteAffiliation(destinationB, communityB, SiteAffiliationRole.RECIPIENT))
                .route(new RouteContract(id("aaa_low_weight"), origin, destinationA, RouteProvider.PALE_MIRROR,
                        ResourceKind.IRON, 8, 8, 24, 1, 8, 0, "seed-a", RouteContractStatus.VALIDATED))
                .route(new RouteContract(id("zzz_high_weight"), origin, destinationB, RouteProvider.PALE_MIRROR,
                        ResourceKind.IRON, 8, 8, 24, 3, 8, 0, "seed-b", RouteContractStatus.VALIDATED));
        addCommunity(builder, communityA);
        addCommunity(builder, communityB);
        WorldState state = builder.build();

        new DomainServices().commands().execute(state, new DomainCommand.AdvanceSimulation(1));

        assertEquals(2, state.economy(communityA).orElseThrow().require(ResourceKind.IRON).incomingFlow());
        assertEquals(6, state.economy(communityB).orElseThrow().require(ResourceKind.IRON).incomingFlow());
    }

    @Test
    void weightedAllocationCostDoesNotScaleWithResourceUnits() {
        WorldObjectId mine = id("large_mine");
        WorldObjectId origin = id("large_origin");
        WorldObjectId destinationA = id("large_destination_a");
        WorldObjectId destinationB = id("large_destination_b");
        WorldObjectId communityA = id("large_community_a");
        WorldObjectId communityB = id("large_community_b");
        WorldStateHydration.Builder builder = WorldStateHydration.builder().schemaVersion(36)
                .facility(new FacilityState(mine, new InfectionSourceId("test:threat"), 1_000_000_000, 100, 0))
                .site(new WorldSite(origin, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL))
                .site(new WorldSite(destinationA, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL))
                .site(new WorldSite(destinationB, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL))
                .affiliation(new SiteAffiliation(origin, mine, SiteAffiliationRole.SUPPLIER))
                .affiliation(new SiteAffiliation(destinationA, communityA, SiteAffiliationRole.RECIPIENT))
                .affiliation(new SiteAffiliation(destinationB, communityB, SiteAffiliationRole.RECIPIENT))
                .route(new RouteContract(id("large_route_a"), origin, destinationA, RouteProvider.PALE_MIRROR,
                        ResourceKind.IRON, 1_000_000_000, 8, 24, 1, 1_000_000_000, 0, "seed-a",
                        RouteContractStatus.VALIDATED))
                .route(new RouteContract(id("large_route_b"), origin, destinationB, RouteProvider.PALE_MIRROR,
                        ResourceKind.IRON, 1_000_000_000, 8, 24, 3, 1_000_000_000, 0, "seed-b",
                        RouteContractStatus.VALIDATED));
        addLargeCommunity(builder, communityA);
        addLargeCommunity(builder, communityB);
        WorldState state = builder.build();

        org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofMillis(250), () ->
                new DomainServices().commands().execute(state, new DomainCommand.AdvanceSimulation(1)));

        assertEquals(250_000_000, state.economy(communityA).orElseThrow().require(ResourceKind.IRON).incomingFlow());
        assertEquals(750_000_000, state.economy(communityB).orElseThrow().require(ResourceKind.IRON).incomingFlow());
    }

    @Test
    void worldSiteRegistrationRejectsOrphanBeforeMutatingState() {
        WorldState state = new WorldState();
        WorldObjectId siteId = id("orphan_site");
        DomainCommand command = new DomainCommand.RegisterWorldSite(
                new WorldSite(siteId, WorldSiteType.STORAGE, OperationalState.OPERATIONAL),
                java.util.List.of(new SiteAffiliation(siteId, id("missing_owner"), SiteAffiliationRole.RECIPIENT)),
                java.util.List.of(), "test:orphan");

        assertThrows(DomainCommandException.class, () -> new DomainServices().commands().execute(state, command));
        assertTrue(state.site(siteId).isEmpty(), "failed registration must not leave a partial site");
    }

    @Test
    void developmentCompletionPreflightsTargetBeforeChangingIntentOrEscrow() {
        WorldObjectId community = id("development_community");
        WorldObjectId place = id("development_place");
        WorldObjectId missingSite = id("missing_development_site");
        ResourceAccount account = new ResourceAccount(100, 50, 0, 0, 0,
                0, 0, 0, 0, ResourceAvailability.AVAILABLE, 10);
        DevelopmentIntent intent = new DevelopmentIntent("pm:development:test", community,
                DevelopmentIntentType.UPGRADE_STOREHOUSE, missingSite, ResourceKind.IRON,
                10, 0, 10, 0, 1, java.util.Set.of(), "test", DevelopmentIntentState.PLANNED, "");
        WorldState state = WorldStateHydration.builder().schemaVersion(36)
                .community(new SettlementCommunity(community)).place(new SettlementPlace(place))
                .binding(new CommunityPlaceBinding(community, place))
                .economy(new SettlementEconomy(community, Map.of(ResourceKind.IRON, account)))
                .security(new SettlementSecurity(community, 50))
                .policy(new SettlementPolicy(community, 10, 5, 8, 2))
                .development(new SettlementDevelopment(community, 0, 0, 0, 0, 0))
                .developmentPolicy(SettlementDevelopmentPolicy.defaults(community))
                .authorityProfile(SettlementAuthorityProfile.pmManaged(community))
                .populationGroup(PopulationGroup.residents("test:residents", community, place,
                        Map.of(SettlementCohort.CIVILIANS, 1)))
                .developmentIntent(intent).build();

        assertThrows(DomainCommandException.class, () -> new DomainServices().commands().execute(state,
                new DomainCommand.CompleteDevelopmentIntent(intent.id())));
        assertEquals(DevelopmentIntentState.PLANNED, intent.state());
        assertEquals(10, account.reserved());
        assertEquals(50, account.stock());
    }

    private static void addCommunity(WorldStateHydration.Builder builder, WorldObjectId community) {
        WorldObjectId place = new WorldObjectId(community.value() + "_place");
        builder.community(new SettlementCommunity(community))
                .place(new SettlementPlace(place))
                .binding(new CommunityPlaceBinding(community, place))
                .economy(new SettlementEconomy(community, Map.of(ResourceKind.IRON,
                        new ResourceAccount(100, 0, 0, 8, 8))))
                .security(new SettlementSecurity(community, 50))
                .policy(new SettlementPolicy(community, 10, 5, 8, 2))
                .development(new SettlementDevelopment(community, 0, 0, 0, 0, 0))
                .developmentPolicy(SettlementDevelopmentPolicy.defaults(community))
                .authorityProfile(SettlementAuthorityProfile.pmManaged(community))
                .populationGroup(PopulationGroup.residents(community.value() + "_residents", community, place,
                        Map.of(SettlementCohort.CIVILIANS, 10)));
    }

    private static void addLargeCommunity(WorldStateHydration.Builder builder, WorldObjectId community) {
        WorldObjectId place = new WorldObjectId(community.value() + "_place");
        builder.community(new SettlementCommunity(community)).place(new SettlementPlace(place))
                .binding(new CommunityPlaceBinding(community, place))
                .economy(new SettlementEconomy(community, Map.of(ResourceKind.IRON,
                        new ResourceAccount(1_000_000_000, 0, 0, 0, 0))))
                .security(new SettlementSecurity(community, 50))
                .policy(new SettlementPolicy(community, 10, 5, 8, 2))
                .development(new SettlementDevelopment(community, 0, 0, 0, 0, 0))
                .developmentPolicy(SettlementDevelopmentPolicy.defaults(community))
                .authorityProfile(SettlementAuthorityProfile.pmManaged(community))
                .populationGroup(PopulationGroup.residents(community.value() + "_residents", community, place,
                        Map.of(SettlementCohort.CIVILIANS, 10)));
    }

    private static WorldObjectId id(String value) {
        return new WorldObjectId("pale_mirror:" + value);
    }
}
