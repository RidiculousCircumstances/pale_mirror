package io.farfrontier.palemirror.internal.presentation.atlas;

import java.util.Comparator;

import io.farfrontier.palemirror.domain.EmergencyWindowState;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.ScenarioInstance;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.KnownRegionalFeature;
import io.farfrontier.palemirror.domain.RouteContractStatus;
import io.farfrontier.palemirror.domain.RouteFreshness;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRecord;
import io.farfrontier.palemirror.internal.network.AtlasSnapshotPayload;
import io.farfrontier.palemirror.internal.world.CampaignRegionRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Bounded server projection for the client Atlas and optional map integrations. */
public final class RegionalAtlasProjection {
    private static final int MAX_REGIONS = 3;

    private RegionalAtlasProjection() { }

    public static AtlasSnapshotPayload snapshot(PaleMirrorSavedData data, StoryAudienceId audience,
                                                String notice, boolean openScreen) {
        CompoundTag root = new CompoundTag();
        root.putLong("step", data.worldState().simulationStep());
        root.putString("notice", limited(notice, 160));
        ListTag regions = new ListTag();
        data.worldState().livingRegions().stream()
                .filter(region -> region.primaryAudience() == null || region.primaryAudience().equals(audience))
                .sorted(Comparator.comparing(region -> region.id()))
                .limit(MAX_REGIONS)
                .forEach(region -> regions.add(region(data, region.id(), region.communityId().value(),
                        region.primaryFacilityId().value(), region.alternateFacilityId().value(),
                        region.primaryRouteId().value(), region.alternateRouteId().value(), audience)));
        root.put("regions", regions);
        return new AtlasSnapshotPayload(root, openScreen);
    }

    private static CompoundTag region(PaleMirrorSavedData data, String regionId, String communityId,
                                      String primaryFacilityId, String alternateFacilityId, String primaryRouteId,
                                      String alternateRouteId, StoryAudienceId audience) {
        CompoundTag value = new CompoundTag();
        CampaignRegionRecord presentation = data.campaignRegions().get(regionId);
        var community = data.worldState().community(new io.farfrontier.palemirror.domain.WorldObjectId(communityId)).orElse(null);
        if (presentation == null || community == null) return value;
        var iron = data.worldState().economy(community.id()).orElseThrow().require(ResourceKind.IRON);
        var security = data.worldState().security(community.id()).orElseThrow();
        var knowledge = data.worldState().regionKnowledge(audience, regionId).orElse(null);
        boolean settlementKnown = knowledge != null && knowledge.knows(KnownRegionalFeature.SETTLEMENT);
        boolean depotKnown = knowledge != null && knowledge.knows(KnownRegionalFeature.DEPOT);
        boolean routeKnown = knowledge != null && knowledge.knows(KnownRegionalFeature.PRIMARY_ROUTE);
        boolean primaryMineKnown = knowledge != null && knowledge.knows(KnownRegionalFeature.PRIMARY_MINE);
        boolean alternateKnown = knowledge != null && knowledge.knows(KnownRegionalFeature.ALTERNATE_SOURCE);
        value.putBoolean("supplyKnown", depotKnown);
        value.putBoolean("primaryRouteKnown", routeKnown);
        value.putString("name", limited(presentation.displayName(), 64));
        value.putString("dimension", limited(presentation.dimensionId(), 96));
        value.putString("community", communityId);
        value.putString("crisis", community.crisisState().name());
        value.putInt("iron", iron.stock());
        value.putInt("ironCapacity", iron.capacity());
        value.putInt("netFlow", iron.netFlow());
        value.putLong("reserve", iron.reserveSteps().orElse(-1L));
        value.putInt("defence", security.defenceReadiness());
        value.putInt("baseDefence", security.baseDefence());
        value.putString("primaryRoute", routeSummary(data, primaryRouteId));
        value.putString("alternateRoute", routeSummary(data, alternateRouteId));
        value.putString("primaryDiagnosis", primaryDiagnosis(data, regionId, primaryFacilityId, primaryRouteId).name());
        value.putString("alternateDiagnosis", alternateDiagnosis(data, alternateRouteId).name());
        var minecart = data.vanillaMinecartRoutes().get(regionId);
        if (routeKnown && minecart != null && minecart.damagedCriticalCellCount() > 0) {
            value.putInt("primaryRepairCount", minecart.damagedCriticalCellCount());
            putPosition(value, "primaryRepair", minecart.firstDamagedCriticalCell(), true);
        }
        data.worldState().routeContract(new io.farfrontier.palemirror.domain.WorldObjectId(primaryRouteId)).ifPresent(route -> {
            value.putInt("primaryCapacity", route.transferableCapacity(data.worldState().simulationStep()));
            value.putInt("primaryNominalCapacity", route.nominalCapacity());
        });
        data.worldState().routeContract(new io.farfrontier.palemirror.domain.WorldObjectId(alternateRouteId)).ifPresent(route -> {
            value.putInt("alternateCapacity", route.transferableCapacity(data.worldState().simulationStep()));
            value.putInt("alternateNominalCapacity", route.nominalCapacity());
        });
        putPosition(value, "settlement", presentation.settlementAnchor(), settlementKnown);
        putPosition(value, "primaryMine", presentation.primaryMineAnchor(), primaryMineKnown && presentation.primaryMineAnchor() != null);
        putPosition(value, "alternateMine", presentation.alternateMineAnchor(), alternateKnown && presentation.alternateMineAnchor() != null);
        SettlementDepotRecord depot = data.settlementDepots().get(community.id());
        putPosition(value, "depot", depot == null ? null : depot.anchor(), depotKnown && depot != null);
        data.worldState().facility(new io.farfrontier.palemirror.domain.WorldObjectId(primaryFacilityId))
                .ifPresent(facility -> value.putString("primaryMineStatus", facility.status().name()));
        data.worldState().emergencyWindow(community.id()).ifPresent(window -> {
            value.putString("emergency", window.state().name());
            value.putLong("deadline", window.deadlineStep());
            value.putLong("remainingGrace", window.remainingGraceSteps());
            value.putString("reachability", window.reachabilityAtOpen().name());
        });
        ScenarioInstance scenario = data.worldState().scenarios().stream()
                .filter(candidate -> candidate.audience().equals(audience) && !candidate.status().isTerminal())
                .filter(candidate -> candidate.target().equals(community.id())
                        || candidate.target().value().equals(primaryFacilityId))
                .sorted(Comparator.comparing(ScenarioInstance::id)).findFirst().orElse(null);
        if (scenario != null) {
            value.putString("scenario", limited(scenario.id(), 160));
            value.putString("scenarioTitle", scenario.archetype().name().toLowerCase(java.util.Locale.ROOT));
            value.putString("scenarioStatus", scenario.status().name());
        }
        data.worldState().developmentIntents().stream()
                .filter(intent -> intent.communityId().equals(community.id())
                        && intent.type() == io.farfrontier.palemirror.domain.DevelopmentIntentType.UPGRADE_STOREHOUSE)
                .findFirst().ifPresent(intent -> {
                    value.putString("developmentState", intent.state().name());
                    value.putInt("developmentRequired", intent.requiredAmount());
                    value.putInt("developmentContributed", intent.contributedAmount());
                    value.putInt("developmentRemaining", intent.remainingAmount());
                    value.putInt("developmentWait", intent.qualifyingWaitSteps());
                    value.putInt("developmentWaitRequired", intent.autonomousWaitRequired());
                });
        boolean canPrepare = data.worldState().emergencyWindow(community.id())
                .map(window -> window.state() == EmergencyWindowState.OPEN).orElse(false);
        value.putBoolean("canPrepareEvacuation", canPrepare);
        value.putBoolean("canBeginEvacuation", canPrepare && preparedShelter(data, community.id()));
        return value;
    }

    private static boolean preparedShelter(PaleMirrorSavedData data,
                                           io.farfrontier.palemirror.domain.WorldObjectId communityId) {
        int population = data.worldState().population(communityId);
        return data.worldState().siteCapabilities().stream()
                .filter(capability -> capability.type() == io.farfrontier.palemirror.domain.SiteCapabilityType.SHELTER)
                .filter(capability -> capability.capacity() >= population)
                .filter(capability -> data.worldState().siteAffiliations(capability.siteId(),
                        io.farfrontier.palemirror.domain.SiteAffiliationRole.RECIPIENT).stream()
                        .anyMatch(affiliation -> affiliation.objectId().equals(communityId)))
                .anyMatch(capability -> data.worldState().site(capability.siteId()).map(site ->
                        site.operationalState() == io.farfrontier.palemirror.domain.OperationalState.OPERATIONAL)
                        .orElse(false));
    }

    private static SupplyDiagnosis primaryDiagnosis(PaleMirrorSavedData data, String regionId,
                                                     String facilityId, String routeId) {
        var physical = data.vanillaMinecartRoutes().get(regionId);
        if (physical == null) return SupplyDiagnosis.BUILDING;
        switch (physical.status()) {
            case PLANNED, BUILDING, VERIFYING -> { return SupplyDiagnosis.BUILDING; }
            case BLOCKED, SUSPENDED -> { return SupplyDiagnosis.ROUTE_DAMAGED; }
            case LEGACY -> { return SupplyDiagnosis.ROUTE_BLOCKED; }
            case ACTIVE -> { }
        }
        var route = data.worldState().routeContract(new io.farfrontier.palemirror.domain.WorldObjectId(routeId)).orElse(null);
        if (route == null || route.status() == RouteContractStatus.BLOCKED || route.status() == RouteContractStatus.CLOSED) {
            return SupplyDiagnosis.ROUTE_BLOCKED;
        }
        var facility = data.worldState().facility(new io.farfrontier.palemirror.domain.WorldObjectId(facilityId)).orElse(null);
        if (facility == null || facility.status() == FacilityStatus.INFECTED) return SupplyDiagnosis.NO_SOURCE;
        if (facility.status() == FacilityStatus.RECOVERING) return SupplyDiagnosis.RECOVERING;
        if (route.freshness(data.worldState().simulationStep()) != RouteFreshness.CURRENT) return SupplyDiagnosis.ROUTE_STALE;
        return route.transferableCapacity(data.worldState().simulationStep()) > 0
                ? SupplyDiagnosis.OPERATIONAL : SupplyDiagnosis.ROUTE_BLOCKED;
    }

    private static SupplyDiagnosis alternateDiagnosis(PaleMirrorSavedData data, String routeId) {
        var route = data.worldState().routeContract(new io.farfrontier.palemirror.domain.WorldObjectId(routeId)).orElse(null);
        if (route == null || route.status() == RouteContractStatus.PLANNED) return SupplyDiagnosis.NOT_ESTABLISHED;
        if (route.status() == RouteContractStatus.BLOCKED || route.status() == RouteContractStatus.CLOSED) {
            return SupplyDiagnosis.ROUTE_BLOCKED;
        }
        if (route.freshness(data.worldState().simulationStep()) != RouteFreshness.CURRENT) return SupplyDiagnosis.ROUTE_STALE;
        return route.transferableCapacity(data.worldState().simulationStep()) > 0
                ? SupplyDiagnosis.OPERATIONAL : SupplyDiagnosis.NOT_ESTABLISHED;
    }

    private static String routeSummary(PaleMirrorSavedData data, String routeId) {
        try {
            return data.worldState().routeContract(new io.farfrontier.palemirror.domain.WorldObjectId(routeId))
                    .map(route -> route.status() + " / " + route.freshness(data.worldState().simulationStep()))
                    .orElse("UNAVAILABLE");
        } catch (IllegalArgumentException ignored) {
            return "UNAVAILABLE";
        }
    }

    private static void putPosition(CompoundTag target, String key, BlockPos position, boolean represented) {
        target.putBoolean(key + "Known", represented);
        if (position == null) return;
        target.putInt(key + "X", position.getX());
        target.putInt(key + "Y", position.getY());
        target.putInt(key + "Z", position.getZ());
    }

    private static String limited(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
