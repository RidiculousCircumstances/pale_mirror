package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.domain.CommunityPlaceBinding;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.GuardCapability;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.PopulationGroup;
import io.farfrontier.palemirror.domain.ResourceAccount;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.RouteContract;
import io.farfrontier.palemirror.domain.RouteContractStatus;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.domain.SettlementAuthorityProfile;
import io.farfrontier.palemirror.domain.SettlementCohort;
import io.farfrontier.palemirror.domain.SettlementCommunity;
import io.farfrontier.palemirror.domain.SettlementDevelopment;
import io.farfrontier.palemirror.domain.SettlementDevelopmentPolicy;
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
import io.farfrontier.palemirror.domain.WorldPath;
import io.farfrontier.palemirror.domain.WorldPathNode;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinition;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinitions;
import io.farfrontier.palemirror.internal.economy.SettlementDepotGeometry;
import io.farfrontier.palemirror.internal.materialization.ParcelRecord;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** Accepts immutable visual genesis facts and creates canonical PM state exactly once. */
public final class AuthoredRegionRegistrar {
    private AuthoredRegionRegistrar() { }

    public static boolean discover(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        if (provider == null) return false;
        boolean changed = false;
        for (AuthoredRegionSeed seed : provider.discoverAuthoredRegions(server.overworld()).stream()
                .sorted(java.util.Comparator.comparing(AuthoredRegionSeed::planId)).toList()) {
            if (!seed.dimensionId().equals(server.overworld().dimension().location().toString())) continue;
            var existing = data.worldState().livingRegion(seed.planId()).orElse(null);
            if (existing != null) {
                if (ensureAuthoredPlace(data, existing.placeId(), seed)) changed = true;
                CampaignRegionRecord presentation = data.campaignRegions().get(seed.planId());
                if (presentation == null) {
                    throw new IllegalStateException("Authored region has no presentation record " + seed.planId());
                }
                if (presentation.reconcileAuthoredDepotAnchors(block(seed.settlementSite().receivingRailhead()),
                        block(seed.settlementSite().depotFunctionalCore()))) {
                    PaleMirrorMod.LOGGER.info("Reconciled authored depot anchors for {}: functional core {}, railhead {}",
                            seed.planId(), seed.settlementSite().depotFunctionalCore(),
                            seed.settlementSite().receivingRailhead());
                    changed = true;
                }
                if (ensureDepotParcel(data, seed, RegionBindings.forAuthored(seed.planId()))) changed = true;
                continue;
            }
            register(data, commands, seed);
            changed = true;
        }
        return changed;
    }

    /** Reconciles generated MineSites only after all of their owning chunks are present. */
    public static boolean reconcilePhysical(MinecraftServer server, PaleMirrorSavedData data) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        if (provider == null || !provider.genesisReadiness().ready()) return false;
        boolean changed = false;
        for (AuthoredRegionSeed seed : provider.discoverAuthoredRegions(server.overworld())) {
            CampaignRegionRecord record = data.campaignRegions().get(seed.planId());
            if (record == null) continue;
            if (provider.authoredMineSiteReady(server.overworld(), seed, seed.alternateMineSite())
                    && io.farfrontier.palemirror.internal.settlement.AuthoredBlueprintSlots.captureAvailable(
                    server.overworld(), data, provider, seed)) changed = true;
            if (record.status() == CampaignRegionPresentationStatus.MATERIALIZED) continue;
            RegionBindings ids = RegionBindings.forAuthored(seed.planId());
            BlockPos primary = block(seed.primaryMineSite().portal());
            BlockPos alternate = block(seed.alternateMineSite().portal());
            if (provider.authoredMineSiteReady(server.overworld(), seed, seed.primaryMineSite())
                    && AuthoredMineSiteObserver.isAreaLoaded(server.overworld(), seed.primaryMineSite())
                    && !data.testMines().containsKey(ids.primaryMineId())) {
                data.registerTestMine(AuthoredMineSiteObserver.observe(server.overworld(), seed.primaryMineSite(),
                        ids.primaryMineId(), io.farfrontier.palemirror.domain.StoryAudienceId.globalTestAudience()));
                changed = true;
            }
            if (provider.authoredMineSiteReady(server.overworld(), seed, seed.alternateMineSite())
                    && AuthoredMineSiteObserver.isAreaLoaded(server.overworld(), seed.alternateMineSite())
                    && !data.testMines().containsKey(ids.alternateMineId())) {
                data.registerTestMine(AuthoredMineSiteObserver.observe(server.overworld(), seed.alternateMineSite(),
                        ids.alternateMineId(), io.farfrontier.palemirror.domain.StoryAudienceId.globalTestAudience()));
                changed = true;
            }
            if (data.testMines().containsKey(ids.primaryMineId()) && data.testMines().containsKey(ids.alternateMineId())) {
                record.observeWorldgenMaterialized(primary, alternate);
                changed = true;
            }
        }
        if (changed) data.setDirty();
        return changed;
    }

    static void register(PaleMirrorSavedData data, DomainCommandExecutor commands, AuthoredRegionSeed seed) {
        RegionBindings ids = RegionBindings.forAuthored(seed.planId());
        if (data.campaignRegions().containsKey(ids.regionId())) return;
        CampaignRegionDefinition definition = CampaignRegionDefinitions.require(CampaignRegionBootstrapper.IRON_FRONTIER_DEFINITION);
        if (!definition.id().toString().equals(seed.archetypeId())) {
            throw new IllegalStateException("Unsupported authored archetype " + seed.archetypeId());
        }
        int population = seed.residents().size();
        int demand = scale(definition.ironDemand(), population, definition.population());
        int initialStock = Math.max(demand * 4, scale(definition.initialIronStock(), population, definition.population()));
        int capacity = Math.max(initialStock, scale(definition.ironStockCapacity(), population, definition.population()));
        WorldObjectId placeId = new WorldObjectId(ids.regionId() + "_place");
        LivingRegionState region = new LivingRegionState(ids.regionId(), ids.communityId(), placeId,
                ids.primaryMineId(), ids.alternateMineId(), ids.primaryRouteId(), ids.alternateRouteId(),
                definition.crisisDelaySteps(), true);
        FacilityState primary = new FacilityState(ids.primaryMineId(), definition.infectionSource(),
                definition.ironProduction(), Integer.MAX_VALUE, 0);
        FacilityState alternate = new FacilityState(ids.alternateMineId(), definition.infectionSource(),
                definition.ironProduction(), Integer.MAX_VALUE, 0);
        SettlementCommunity community = new SettlementCommunity(ids.communityId());
        SettlementPlace place = new SettlementPlace(placeId);
        SettlementEconomy economy = new SettlementEconomy(ids.communityId(), Map.of(ResourceKind.IRON,
                new ResourceAccount(capacity, initialStock, 0, demand,
                        Math.min(demand, scale(definition.rationedIronDemand(), population, definition.population())))));
        int guards = (int) seed.residents().stream().filter(value -> value.cohort().equals("GUARDS")).count();
        SettlementSecurity security = new SettlementSecurity(ids.communityId(), definition.defence(), definition.defence(),
                guards, guards > 0 ? GuardCapability.PRESENT : GuardCapability.ABSENT);
        SettlementPolicy policy = new SettlementPolicy(ids.communityId(), definition.rationReserveSteps(),
                definition.requestReserveSteps(), definition.defenceLossPerUnavailableStep(),
                definition.stableStepsToRecover(), definition.evacuationDefenceThreshold(),
                definition.emergencyGraceSteps(), definition.evacuationDurationSteps());
        List<WorldSite> sites = new java.util.ArrayList<>(List.of(
                new WorldSite(ids.primaryDispatchSiteId(), WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                new WorldSite(ids.alternateDispatchSiteId(), WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.DEGRADED),
                new WorldSite(ids.receivingSiteId(), WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL)));
        List<SiteAffiliation> affiliations = new java.util.ArrayList<>(List.of(
                new SiteAffiliation(ids.primaryDispatchSiteId(), ids.primaryMineId(), SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(ids.alternateDispatchSiteId(), ids.alternateMineId(), SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(ids.receivingSiteId(), ids.communityId(), SiteAffiliationRole.RECIPIENT)));
        List<SiteCapability> capabilities = new java.util.ArrayList<>(List.of(
                new SiteCapability(ids.primaryDispatchSiteId(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()),
                new SiteCapability(ids.alternateDispatchSiteId(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()),
                new SiteCapability(ids.receivingSiteId(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction())));
        List<WorldPath> paths = new java.util.ArrayList<>();
        for (int index = 0; index < seed.expansionPlots().size(); index++) {
            WorldObjectId plotId = developmentPlotId(ids.regionId(), index);
            sites.add(new WorldSite(plotId, WorldSiteType.DEVELOPMENT, OperationalState.DEGRADED));
            affiliations.add(new SiteAffiliation(plotId, ids.communityId(), SiteAffiliationRole.RECIPIENT));
        }
        for (int index = 0; index < seed.shelterCandidates().size(); index++) {
            WorldObjectId shelterId = new WorldObjectId(ids.regionId() + "_shelter_candidate_" + index);
            VisualPoint shelter = seed.shelterCandidates().get(index);
            sites.add(new WorldSite(shelterId, WorldSiteType.SHELTER, OperationalState.DEGRADED));
            affiliations.add(new SiteAffiliation(shelterId, ids.communityId(), SiteAffiliationRole.RECIPIENT));
            capabilities.add(new SiteCapability(shelterId, SiteCapabilityType.SHELTER, null, population));
            paths.add(new WorldPath(ids.regionId() + ":evacuation_path_" + index, seed.contentHash(),
                    ids.receivingSiteId(), shelterId, pathNodes(seed, shelter, index)));
            List<WorldPathNode> returnNodes = new java.util.ArrayList<>(pathNodes(seed, shelter, index));
            java.util.Collections.reverse(returnNodes);
            paths.add(new WorldPath(ids.regionId() + ":return_path_" + index, seed.contentHash(),
                    shelterId, ids.receivingSiteId(), returnNodes));
        }
        List<RouteContract> routes = List.of(
                RouteContract.authoredVanillaMinecart(ids.primaryRouteId(), ids.primaryDispatchSiteId(),
                        ids.receivingSiteId(), ResourceKind.IRON, definition.ironProduction(),
                        definition.routeCurrentWindowSteps(), definition.routeExpiryWindowSteps(),
                        data.worldState().simulationStep(), "authored-topology:" + seed.contentHash()),
                new RouteContract(ids.alternateRouteId(), ids.alternateDispatchSiteId(), ids.receivingSiteId(),
                        RouteProvider.CREATE, ResourceKind.IRON, definition.ironProduction(),
                        definition.routeCurrentWindowSteps(), definition.routeExpiryWindowSteps(), RouteContractStatus.PLANNED));
        PopulationGroup residents = PopulationGroup.residents(ids.regionId() + ":residents", ids.communityId(), placeId,
                cohortCounts(seed));
        commands.execute(data.worldState(), new DomainCommand.RegisterLivingRegion(region, List.of(primary, alternate),
                community, place, new CommunityPlaceBinding(ids.communityId(), placeId), economy, security, policy,
                sites, affiliations, capabilities, routes, List.of(residents), paths,
                new SettlementDevelopment(ids.communityId(), 25, 0, population, Math.max(population, 56), 0),
                SettlementDevelopmentPolicy.defaults(ids.communityId()),
                SettlementAuthorityProfile.pmManaged(ids.communityId())));
        ensureAuthoredPlace(data, placeId, seed);
        data.campaignRegions().put(ids.regionId(), record(seed, placeId, definition));
        registerParcels(data, seed, ids);
        List<BlockPos> freightPath = seed.baselineRailNodes().stream().map(AuthoredRegionRegistrar::block)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        java.util.Collections.reverse(freightPath);
        data.vanillaMinecartRoutes().put(seed.planId(), VanillaMinecartRouteRecord.authored(seed.planId(),
                seed.dimensionId(), ids.primaryRouteId().value(), freightPath));
        data.setDirty();
    }

    /**
     * The immutable genesis manifest is already sufficient physical identity for an authored place. Chunks may be
     * generated later, but discovery, Atlas coordinates, and bounded navigation must not wait for a second village
     * observation that can never exist for a PM-authored settlement.
     */
    private static boolean ensureAuthoredPlace(PaleMirrorSavedData data, WorldObjectId placeId,
                                               AuthoredRegionSeed seed) {
        var bounds = seed.settlementBounds();
        return ensureAuthoredPlace(data.worldRegistry(), placeId, seed.dimensionId(), block(seed.anchor()),
                block(bounds.min()), block(bounds.max()), seed.definitionVersion() + ":" + seed.contentHash());
    }

    static boolean ensureAuthoredPlace(WorldObjectRegistry registry, WorldObjectId placeId, String dimensionId,
                                       BlockPos anchor, BlockPos minimum, BlockPos maximum, String version) {
        var existing = registry.find(placeId).orElse(null);
        String template = "pale_mirror:authored_settlement";
        if (existing == null) {
            registry.register(new WorldObjectRegistryEntry(placeId, dimensionId, anchor,
                    minimum, maximum, template, version, WorldObjectLifecycle.REPRESENTED));
            return true;
        }
        if (!existing.dimensionId().equals(dimensionId) || !existing.anchor().equals(anchor)
                || !existing.minBounds().equals(minimum) || !existing.maxBounds().equals(maximum)
                || !existing.templateId().equals(template) || !existing.templateVersion().equals(version)) {
            throw new IllegalStateException("Authored place registry conflicts with genesis manifest " + placeId);
        }
        return false;
    }

    private static void registerParcels(PaleMirrorSavedData data, AuthoredRegionSeed seed, RegionBindings ids) {
        for (int index = 0; index < seed.settlementSite().managedArea().areas().size(); index++) {
            data.parcels().register(parcel(seed, "influence_" + index,
                    seed.settlementSite().managedArea().areas().get(index),
                    "settlement_managed_area", ParcelKind.INFLUENCE));
        }
        for (int index = 0; index < seed.modules().size(); index++) {
            var module = seed.modules().get(index);
            data.parcels().register(parcel(seed, "module_" + index, module.footprint(), module.templateId(), ParcelKind.COMMUNITY));
        }
        ensureDepotParcel(data, seed, ids);
        registerMineParcels(data, seed, seed.primaryMineSite(), "primary_mine", false);
        registerMineParcels(data, seed, seed.alternateMineSite(), "alternate_mine", true);
        for (int index = 0; index < seed.expansionPlots().size(); index++) {
            data.parcels().register(parcel(seed, "expansion_" + index, seed.expansionPlots().get(index),
                    developmentPlotId(seed.planId(), index).value(), ParcelKind.RESERVED));
        }
        for (int index = 0; index < seed.shelterCandidates().size(); index++) {
            VisualPoint point = seed.shelterCandidates().get(index);
            VisualBounds bounds = new VisualBounds(new VisualPoint(point.x() - 8, point.y() - 2, point.z() - 8),
                    new VisualPoint(point.x() + 8, point.y() + 12, point.z() + 8));
            data.parcels().register(parcel(seed, "shelter_" + index, bounds, "shelter_candidate_" + index,
                    ParcelKind.RESERVED));
        }
        for (int from = 0; from < seed.baselineRailNodes().size(); from += 16) {
            int to = Math.min(seed.baselineRailNodes().size(), from + 16);
            List<VisualPoint> slice = seed.baselineRailNodes().subList(from, to);
            int minX = slice.stream().mapToInt(VisualPoint::x).min().orElseThrow() - 2;
            int maxX = slice.stream().mapToInt(VisualPoint::x).max().orElseThrow() + 2;
            int minY = slice.stream().mapToInt(VisualPoint::y).min().orElseThrow() - 32;
            int maxY = slice.stream().mapToInt(VisualPoint::y).max().orElseThrow() + 4;
            int minZ = slice.stream().mapToInt(VisualPoint::z).min().orElseThrow() - 2;
            int maxZ = slice.stream().mapToInt(VisualPoint::z).max().orElseThrow() + 2;
            data.parcels().register(new ParcelRecord(seed.planId() + ":parcel:rail_" + from, seed.planId(),
                    seed.dimensionId(), new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                    "baseline_rail", ParcelKind.PUBLIC_INFRASTRUCTURE, null, 0, ""));
        }
    }

    private static boolean ensureDepotParcel(PaleMirrorSavedData data, AuthoredRegionSeed seed, RegionBindings ids) {
        String parcelId = SettlementDepotGeometry.parcelId(seed.planId());
        if (data.parcels().find(parcelId).isPresent()) return false;
        BlockPos anchor = block(seed.settlementSite().depotFunctionalCore());
        data.parcels().register(new ParcelRecord(parcelId, seed.planId(), seed.dimensionId(),
                SettlementDepotGeometry.parcelMin(anchor), SettlementDepotGeometry.parcelMax(anchor),
                ids.communityId().value() + "_supply_depot", ParcelKind.COMMUNITY, null, 0, ""));
        return true;
    }

    private static void registerMineParcels(PaleMirrorSavedData data, AuthoredRegionSeed seed,
                                            io.farfrontier.palemirror.api.AuthoredMineSitePlan mine,
                                            String prefix, boolean stagedReserved) {
        data.parcels().register(parcel(seed, prefix + "_influence", mine.bounds(), mine.siteId(), ParcelKind.INFLUENCE));
        for (int index = 0; index < mine.initialModules().size(); index++) {
            var module = mine.initialModules().get(index);
            data.parcels().register(parcel(seed, prefix + "_module_" + index, module.footprint(),
                    mine.siteId() + ":" + module.role().toLowerCase(java.util.Locale.ROOT), ParcelKind.COMMUNITY));
        }
        if (!stagedReserved) return;
        for (int index = 0; index < mine.stagedModules().size(); index++) {
            var stage = mine.stagedModules().get(index);
            data.parcels().register(parcel(seed, prefix + "_stage_" + index, stage.module().footprint(),
                    mine.siteId() + ":stage:" + stage.stage(), ParcelKind.RESERVED));
        }
    }

    private static ParcelRecord parcel(AuthoredRegionSeed seed, String suffix, VisualBounds bounds,
                                       String binding, ParcelKind kind) {
        return new ParcelRecord(seed.planId() + ":parcel:" + suffix, seed.planId(), seed.dimensionId(),
                block(bounds.min()), block(bounds.max()), binding, kind, null, 0, "");
    }
    private static WorldObjectId developmentPlotId(String regionId, int index) {
        return new WorldObjectId(regionId + "_development_plot_" + index);
    }

    private static CampaignRegionRecord record(AuthoredRegionSeed seed, WorldObjectId placeId,
                                               CampaignRegionDefinition definition) {
        return new CampaignRegionRecord(seed.planId(), definition.id().toString(), Math.max(2, definition.version()),
                displayName(seed.planId()), seed.dimensionId(), placeId, block(seed.anchor()), block(seed.primaryMine()),
                block(seed.alternateMine()), block(seed.settlementSite().receivingRailhead()),
                block(seed.settlementSite().depotFunctionalCore()), null, null,
                CampaignRegionPresentationStatus.PLANNED, "", 0, -1, -1, 0, 0, "", "");
    }

    private static Map<SettlementCohort, Integer> cohortCounts(AuthoredRegionSeed seed) {
        Map<SettlementCohort, Integer> result = new EnumMap<>(SettlementCohort.class);
        seed.residents().forEach(value -> result.merge(SettlementCohort.valueOf(value.cohort()), 1, Integer::sum));
        return result;
    }

    private static List<WorldPathNode> pathNodes(AuthoredRegionSeed seed, VisualPoint shelter, int index) {
        VisualPoint start = seed.freightGate();
        VisualPoint middle = new VisualPoint((start.x() + shelter.x()) / 2, (start.y() + shelter.y()) / 2,
                (start.z() + shelter.z()) / 2);
        return List.of(new WorldPathNode("gate", seed.dimensionId(), start.x(), start.y(), start.z(), true),
                new WorldPathNode("staging_" + index, seed.dimensionId(), middle.x(), middle.y(), middle.z(), true),
                new WorldPathNode("shelter_" + index, seed.dimensionId(), shelter.x(), shelter.y(), shelter.z(), true));
    }

    private static int scale(int value, int population, int baseline) {
        return (value * population + baseline - 1) / baseline;
    }

    private static BlockPos block(VisualPoint point) { return new BlockPos(point.x(), point.y(), point.z()); }
    private static String displayName(String planId) {
        String suffix = planId.substring(planId.lastIndexOf('_') + 1).toUpperCase(java.util.Locale.ROOT);
        return "Frontier " + suffix;
    }
}
