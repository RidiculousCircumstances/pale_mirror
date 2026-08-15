package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.CommunityPlaceBinding;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.PopulationGroup;
import io.farfrontier.palemirror.domain.RecognitionState;
import io.farfrontier.palemirror.domain.ResourceAccount;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.RouteContract;
import io.farfrontier.palemirror.domain.RouteContractStatus;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.domain.SettlementCohort;
import io.farfrontier.palemirror.domain.SettlementCommunity;
import io.farfrontier.palemirror.domain.SettlementEconomy;
import io.farfrontier.palemirror.domain.SettlementPlace;
import io.farfrontier.palemirror.domain.SettlementPolicy;
import io.farfrontier.palemirror.domain.SettlementSecurity;
import io.farfrontier.palemirror.domain.SiteAffiliation;
import io.farfrontier.palemirror.domain.SiteAffiliationRole;
import io.farfrontier.palemirror.domain.SiteCapability;
import io.farfrontier.palemirror.domain.SiteCapabilityType;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldSite;
import io.farfrontier.palemirror.domain.WorldSiteType;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import java.util.List;
import java.util.Map;

/** Test-only canonical reset that still crosses the normal domain command boundary. */
final class GameTestStateReset {
    private GameTestStateReset() {}

    static void resetCanonical(PaleMirrorSavedData data) {
        new DomainServices().commands().execute(data.worldState(),
                new DomainCommand.ResetWorldState("gametest:reset"));
    }

    static void registerMinimalCommunity(PaleMirrorSavedData data, WorldObjectId communityId) {
        String prefix = communityId.value() + "_fixture";
        registerMinimalRegion(data, communityId, prefix, prefix + "_region",
                new WorldObjectId(prefix + "_primary_route"));
    }

    static void registerMinimalRouteRegion(PaleMirrorSavedData data, String regionId, WorldObjectId routeId) {
        registerMinimalRouteRegion(data, regionId, routeId, RouteProvider.PALE_MIRROR);
    }

    static void registerMinimalRouteRegion(PaleMirrorSavedData data, String regionId, WorldObjectId routeId,
                                           RouteProvider primaryProvider) {
        String namespace = routeId.value().substring(0, routeId.value().indexOf(':'));
        String prefix = namespace + ":fixture_" + Integer.toUnsignedString(regionId.hashCode(), 36);
        registerMinimalRegion(data, new WorldObjectId(prefix + "_community"), prefix, regionId, routeId,
                primaryProvider);
    }

    private static void registerMinimalRegion(PaleMirrorSavedData data, WorldObjectId communityId,
                                              String prefix, String regionId, WorldObjectId primaryRouteId) {
        registerMinimalRegion(data, communityId, prefix, regionId, primaryRouteId, RouteProvider.PALE_MIRROR);
    }

    private static void registerMinimalRegion(PaleMirrorSavedData data, WorldObjectId communityId,
                                              String prefix, String regionId, WorldObjectId primaryRouteId,
                                              RouteProvider primaryProvider) {
        WorldObjectId placeId = new WorldObjectId(prefix + "_place");
        WorldObjectId primaryFacilityId = new WorldObjectId(prefix + "_primary_mine");
        WorldObjectId alternateFacilityId = new WorldObjectId(prefix + "_alternate_mine");
        WorldObjectId originId = new WorldObjectId(prefix + "_origin");
        WorldObjectId alternateOriginId = new WorldObjectId(prefix + "_alternate_origin");
        WorldObjectId destinationId = new WorldObjectId(prefix + "_destination");
        WorldObjectId alternateRouteId = new WorldObjectId(prefix + "_alternate_route");
        SettlementCommunity community = new SettlementCommunity(communityId);
        SettlementPlace place = new SettlementPlace(placeId);
        PopulationGroup population = PopulationGroup.residents(prefix + "_residents", communityId, placeId,
                Map.of(SettlementCohort.CIVILIANS, 4));
        List<WorldSite> sites = List.of(
                new WorldSite(originId, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                new WorldSite(alternateOriginId, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                new WorldSite(destinationId, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL));
        List<SiteAffiliation> affiliations = List.of(
                new SiteAffiliation(originId, primaryFacilityId, SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(alternateOriginId, alternateFacilityId, SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(destinationId, communityId, SiteAffiliationRole.RECIPIENT));
        List<SiteCapability> capabilities = sites.stream()
                .map(site -> new SiteCapability(site.id(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, 8)).toList();
        List<RouteContract> routes = List.of(
                new RouteContract(primaryRouteId, originId, destinationId, primaryProvider,
                        ResourceKind.IRON, 8, 4, 8, RouteContractStatus.PLANNED),
                new RouteContract(alternateRouteId, alternateOriginId, destinationId, RouteProvider.PALE_MIRROR,
                        ResourceKind.IRON, 8, 4, 8, RouteContractStatus.PLANNED));
        LivingRegionState region = new LivingRegionState(regionId, communityId, placeId,
                primaryFacilityId, alternateFacilityId, primaryRouteId, alternateRouteId,
                4, null, RecognitionState.DISCOVERED, -1);
        new DomainServices().commands().execute(data.worldState(), new DomainCommand.RegisterLivingRegion(region,
                List.of(new FacilityState(primaryFacilityId, new InfectionSourceId("pale_mirror:test"), 8, 10, 0),
                        new FacilityState(alternateFacilityId, new InfectionSourceId("pale_mirror:test"), 8, 10, 0)),
                community, place, new CommunityPlaceBinding(communityId, placeId),
                new SettlementEconomy(communityId, Map.of(ResourceKind.IRON, new ResourceAccount(64, 32, 0, 4, 2))),
                new SettlementSecurity(communityId, 40, 40, 1,
                        io.farfrontier.palemirror.domain.GuardCapability.PRESENT),
                new SettlementPolicy(communityId, 8, 4, 2, 3),
                sites, affiliations, capabilities, routes, List.of(population)));
    }

    static void resetAll(PaleMirrorSavedData data) {
        data.testMines().clear();
        data.audienceMappings().clear();
        data.reconciliationLedger().clear();
        data.worldRegistry().clear();
        data.effectLeases().clear();
        data.quarantine().clear();
        data.threatCombat().clear();
        data.campaignRegions().clear();
        data.settlementObservations().clear();
        data.resourceTransfers().clear();
        data.settlementDepots().clear();
        data.refugeeCamps().clear();
        data.refugeeAnchorPermits().clear();
        data.campaignCommissioning().clear();
        data.vanillaMinecartRoutes().clear();
        data.materializationJobs().clear();
        data.semanticSlots().clear();
        data.residentJourneyLeases().clear();
        data.residentIdentities().clear();
        data.parcels().clear();
        resetCanonical(data);
        data.setDirty();
    }
}
