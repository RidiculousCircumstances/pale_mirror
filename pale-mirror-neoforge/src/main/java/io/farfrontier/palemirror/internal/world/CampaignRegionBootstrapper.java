package io.farfrontier.palemirror.internal.world;

import java.util.List;
import io.farfrontier.palemirror.domain.CommunityPlaceBinding;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.ObservationFreshness;
import io.farfrontier.palemirror.domain.RecognitionState;
import io.farfrontier.palemirror.domain.ResourceAccount;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.RouteContract;
import io.farfrontier.palemirror.domain.RouteContractStatus;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.domain.SettlementCommunity;
import io.farfrontier.palemirror.domain.SettlementEconomy;
import io.farfrontier.palemirror.domain.SettlementPlace;
import io.farfrontier.palemirror.domain.SettlementPolicy;
import io.farfrontier.palemirror.domain.SettlementSecurity;
import io.farfrontier.palemirror.domain.GuardCapability;
import io.farfrontier.palemirror.domain.SiteAffiliation;
import io.farfrontier.palemirror.domain.SiteAffiliationRole;
import io.farfrontier.palemirror.domain.SiteCapability;
import io.farfrontier.palemirror.domain.SiteCapabilityType;
import io.farfrontier.palemirror.domain.PopulationGroup;
import io.farfrontier.palemirror.domain.SettlementCohort;
import io.farfrontier.palemirror.domain.SettlementDevelopment;
import io.farfrontier.palemirror.domain.SettlementDevelopmentPolicy;
import io.farfrontier.palemirror.domain.SettlementAuthorityProfile;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldSite;
import io.farfrontier.palemirror.domain.WorldSiteType;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinition;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinitions;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Binds the first authored region to a read-only observed settlement; it never builds a village. */
public final class CampaignRegionBootstrapper {
    public static final String IRONHILL_ID = "pale_mirror:ironhill_v2";
    /** v28 authored archetype; the v27 Ironhill id remains a migration-only instance. */
    public static final String IRON_FRONTIER_ARCHETYPE_ID = "pale_mirror:iron_frontier";
    public static final WorldObjectId IRONHILL = new WorldObjectId("pale_mirror:ironhill_community");
    public static final WorldObjectId MINE17 = new WorldObjectId("pale_mirror:mine17");
    public static final WorldObjectId RED_VALLEY = new WorldObjectId("pale_mirror:red_valley_ironworks");
    public static final WorldObjectId MINE17_ROUTE = new WorldObjectId("pale_mirror:mine17_to_ironhill");
    public static final WorldObjectId RED_VALLEY_ROUTE = new WorldObjectId("pale_mirror:red_valley_to_ironhill");
    public static final WorldObjectId MINE17_DISPATCH_SITE = new WorldObjectId("pale_mirror:mine17_dispatch");
    public static final WorldObjectId RED_VALLEY_DISPATCH_SITE = new WorldObjectId("pale_mirror:red_valley_dispatch");
    public static final WorldObjectId IRONHILL_RECEIVING_SITE = new WorldObjectId("pale_mirror:ironhill_receiving");
    public static final String RED_VALLEY_DISPATCH = "PM Red Valley Dispatch";
    public static final String IRONHILL_RECEIVING = "PM Ironhill Receiving";
    static final ResourceLocation IRON_FRONTIER_DEFINITION = ResourceLocation.parse(IRON_FRONTIER_ARCHETYPE_ID);
    private static final int PRIMARY_MINE_DISTANCE = 640;
    private static final int ALTERNATE_MINE_DISTANCE = 704;
    public static final int MAX_ACTIVE_REGIONS = 3;
    public static final int MIN_REGION_SPACING = 2_048;
    private static final String[] DISPLAY_NAMES = {"Ironhill", "Redvale", "Stonecross", "Ashford", "Greyhaven"};
    private CampaignRegionBootstrapper() { }

    public static void tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands) {
        tick(server, data, commands, true);
    }

    public static void tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands,
                            boolean automaticBinding) {
        if (automaticBinding) ensureCanonicalPlans(server, data, commands);
        data.campaignRegions().values().stream().sorted(java.util.Comparator.comparing(CampaignRegionRecord::id))
                .forEach(record -> advancePhysicalPlan(server.overworld(), data, record));
    }

    /** Advances persisted PM-authored site work without requiring a player near either planned mine. */
    public static void advancePhysicalPlan(ServerLevel level, PaleMirrorSavedData data, CampaignRegionRecord record) {
        if (record.status() == CampaignRegionPresentationStatus.MATERIALIZED) {
            return;
        }
        if (!level.hasChunkAt(record.pendingMineColumn())) return;
        if (record.status() == CampaignRegionPresentationStatus.BLOCKED) {
            if (record.pendingMineAnchorResolved()
                    && record.diagnostic().startsWith("MineSite changed after planning at ")) {
                record.retryBlockedFirstGenerationExecution();
                data.setDirty();
            } else {
                if (record.pendingMineAnchorResolved() || !record.diagnostic().startsWith("MineSite conflict at ")) {
                    return;
                }
                BlockPos recoveryAnchor = resolveAnchor(level, record.pendingMineColumn());
                if (!CampaignMineSiteTemplate.isAreaLoaded(level, recoveryAnchor)) return;
                try {
                    // Re-evaluate without changing state. A genuine crafted/unknown conflict remains BLOCKED and
                    // does not become a hot retry loop; a terrain classification fixed by an update may resume.
                    CampaignMineSiteTemplate.captureBaseline(level, recoveryAnchor);
                    record.retryBlockedPreflight();
                } catch (IllegalStateException stillBlocked) {
                    return;
                }
            }
        }
        if (!record.pendingMineAnchorResolved()) {
            BlockPos anchor = resolveAnchor(level, record.pendingMineColumn());
            if (!CampaignMineSiteTemplate.isAreaLoaded(level, anchor)) return;
            try {
                record.resolvePendingMineAnchor(anchor, CampaignMineSiteTemplate.captureBaseline(level, anchor));
            } catch (IllegalStateException conflict) {
                record.block(conflict.getMessage());
            }
            data.setDirty();
            return;
        }
        if (record.status() == CampaignRegionPresentationStatus.PLANNED) {
            record.startOperation();
            data.setDirty();
            return;
        }
        try {
            RegionBindings bindings = RegionBindings.fromRegionId(record.id());
            if (record.nextOperationIndex() == 0) ensureMine(data, level, record.primaryMineAnchor(), bindings.primaryMineId(), record.pendingMineBaseline());
            else if (record.nextOperationIndex() == 1) ensureMine(data, level, record.alternateMineAnchor(), bindings.alternateMineId(), record.pendingMineBaseline());
            else throw new IllegalStateException("Invalid campaign operation index " + record.nextOperationIndex());
            record.completedOperation();
            data.setDirty();
        } catch (IllegalStateException failure) {
            record.block(failure.getMessage());
            data.setDirty();
        }
    }

    public static void stop(MinecraftServer server) { }

    private static void ensureCanonicalPlans(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands) {
        if (data.worldState().livingRegions().size() >= MAX_ACTIVE_REGIONS) return;
        long gameTime = server.overworld().getGameTime();
        data.settlementObservations().values().stream()
                .filter(value -> value.dimensionId().equals(server.overworld().dimension().location().toString()))
                .filter(SettlementObservationRecord::strongEnoughForRecognition)
                .filter(value -> value.freshness(gameTime) == ObservationFreshness.CURRENT)
                .filter(AdapterRegistry::campaignEligible)
                .filter(value -> productLocationEligible(server.overworld(), value.anchor()))
                .filter(value -> !boundPlace(data, value.id()))
                .filter(value -> correctlySpaced(data, value.anchor()))
                .sorted(java.util.Comparator.comparing(value -> value.id().value()))
                .limit(MAX_ACTIVE_REGIONS - data.worldState().livingRegions().size())
                .forEach(observed -> registerCanonicalPlan(server, data, commands, observed));
    }

    /** Explicit operator selection still uses the exact production registration pipeline. */
    public static void bindCandidate(MinecraftServer server, PaleMirrorSavedData data,
                                     DomainCommandExecutor commands, WorldObjectId candidateId) {
        if (data.worldState().livingRegions().size() >= MAX_ACTIVE_REGIONS) {
            throw new IllegalStateException("The active living-region limit of " + MAX_ACTIVE_REGIONS + " has been reached");
        }
        SettlementObservationRecord observed = data.settlementObservations().get(candidateId);
        if (observed == null) throw new IllegalArgumentException("Unknown observed settlement " + candidateId.value());
        if (!observed.dimensionId().equals(server.overworld().dimension().location().toString())) {
            throw new IllegalArgumentException("The first living region currently requires an Overworld settlement");
        }
        if (!observed.strongEnoughForRecognition()) {
            throw new IllegalStateException("Settlement evidence is not STRONG yet (loaded "
                    + observed.loadedDurationTicks() + "/" + SettlementObservationRecord.MEMBERSHIP_WINDOW_TICKS + " ticks)");
        }
        if (observed.freshness(server.overworld().getGameTime()) != ObservationFreshness.CURRENT) {
            throw new IllegalStateException("Settlement evidence is not CURRENT; revisit the village before binding");
        }
        if (!AdapterRegistry.campaignEligible(observed)) {
            throw new IllegalStateException("Settlement adapter is not eligible for the living-region campaign");
        }
        if (boundPlace(data, observed.id())) throw new IllegalStateException("Settlement already belongs to a living region");
        if (!correctlySpaced(data, observed.anchor())) throw new IllegalStateException("Settlement is within "
                + MIN_REGION_SPACING + " blocks of an existing living region");
        registerCanonicalPlan(server, data, commands, observed);
    }

    private static void registerCanonicalPlan(MinecraftServer server, PaleMirrorSavedData data,
                                              DomainCommandExecutor commands, SettlementObservationRecord observed) {
        RegionBindings bindings = RegionBindings.forObserved(server.overworld().getSeed(), observed.id());
        if (data.worldState().livingRegion(bindings.regionId()).isPresent()) return;
        CampaignRegionDefinition definition = CampaignRegionDefinitions.require(IRON_FRONTIER_DEFINITION);
        WorldObjectId placeId = observed.id();
        int population = Math.max(1, observed.observedPopulation());
        int ironDemand = Math.max(1, scale(definition.ironDemand(), population, definition.population()));
        int initialStock = Math.max(ironDemand * 4, scale(definition.initialIronStock(), population, definition.population()));
        int capacity = Math.max(initialStock, scale(definition.ironStockCapacity(), population, definition.population()));
        BlockPos infrastructureAnchor = infrastructureAnchor(server, observed.anchor(), bindings.regionId());
        BlockPos primaryMineColumn = mineColumn(server, infrastructureAnchor, PRIMARY_MINE_DISTANCE, bindings.regionId());
        BlockPos receivingTerminal = receivingTerminal(server.overworld(), infrastructureAnchor, primaryMineColumn);
        BlockPos plannedDepot = plannedDepotAnchor(server.overworld(), receivingTerminal, primaryMineColumn);
        if (definition.version() >= 2 && plannedDepot == null) return;
        LivingRegionState region = new LivingRegionState(bindings.regionId(), bindings.communityId(), placeId,
                bindings.primaryMineId(), bindings.alternateMineId(), bindings.primaryRouteId(), bindings.alternateRouteId(),
                definition.crisisDelaySteps(), null, RecognitionState.DISCOVERED, -1, definition.version() >= 2);
        FacilityState primary = new FacilityState(bindings.primaryMineId(), definition.infectionSource(), definition.ironProduction(), Integer.MAX_VALUE, 0);
        FacilityState alternate = new FacilityState(bindings.alternateMineId(), definition.infectionSource(), definition.ironProduction(), Integer.MAX_VALUE, 0);
        SettlementCommunity community = new SettlementCommunity(bindings.communityId());
        int guards = Math.min(population, observed.registeredGuards());
        PopulationGroup residents = PopulationGroup.residents(bindings.regionId() + ":residents", bindings.communityId(), placeId,
                java.util.Map.of(SettlementCohort.CIVILIANS, population - guards, SettlementCohort.GUARDS, guards));
        SettlementPlace place = new SettlementPlace(placeId);
        int rationedDemand = Math.min(ironDemand, Math.max(0,
                scale(definition.rationedIronDemand(), population, definition.population())));
        SettlementEconomy economy = new SettlementEconomy(bindings.communityId(), java.util.Map.of(ResourceKind.IRON,
                new ResourceAccount(capacity, initialStock, 0, ironDemand, rationedDemand)));
        SettlementSecurity security = new SettlementSecurity(bindings.communityId(), definition.defence(), definition.defence(),
                observed.registeredGuards(), observed.registeredGuards() > 0 ? GuardCapability.PRESENT : GuardCapability.ABSENT);
        SettlementPolicy policy = new SettlementPolicy(bindings.communityId(), definition.rationReserveSteps(),
                definition.requestReserveSteps(), definition.defenceLossPerUnavailableStep(), definition.stableStepsToRecover(),
                definition.evacuationDefenceThreshold(), definition.emergencyGraceSteps(), definition.evacuationDurationSteps());
        List<WorldSite> sites = List.of(
                new WorldSite(bindings.primaryDispatchSiteId(), WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                new WorldSite(bindings.alternateDispatchSiteId(), WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                new WorldSite(bindings.receivingSiteId(), WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL));
        List<SiteAffiliation> affiliations = List.of(
                new SiteAffiliation(bindings.primaryDispatchSiteId(), bindings.primaryMineId(), SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(bindings.alternateDispatchSiteId(), bindings.alternateMineId(), SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(bindings.receivingSiteId(), bindings.communityId(), SiteAffiliationRole.RECIPIENT));
        List<SiteCapability> capabilities = List.of(
                new SiteCapability(bindings.primaryDispatchSiteId(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()),
                new SiteCapability(bindings.alternateDispatchSiteId(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()),
                new SiteCapability(bindings.receivingSiteId(), SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()));
        List<RouteContract> routes = List.of(
                new RouteContract(bindings.primaryRouteId(), bindings.primaryDispatchSiteId(), bindings.receivingSiteId(), RouteProvider.VANILLA_MINECART,
                        ResourceKind.IRON, definition.ironProduction(), definition.routeCurrentWindowSteps(),
                        definition.routeExpiryWindowSteps(), RouteContractStatus.PLANNED),
                new RouteContract(bindings.alternateRouteId(), bindings.alternateDispatchSiteId(), bindings.receivingSiteId(), RouteProvider.CREATE,
                        ResourceKind.IRON, definition.ironProduction(), definition.routeCurrentWindowSteps(),
                        definition.routeExpiryWindowSteps(), RouteContractStatus.PLANNED));
        SettlementAuthorityProfile authority = "pale_mirror:native_reconciled".equals(observed.authorityProfileId())
                ? SettlementAuthorityProfile.nativeReconciled(bindings.communityId())
                : SettlementAuthorityProfile.pmManaged(bindings.communityId());
        commands.execute(data.worldState(), new DomainCommand.RegisterLivingRegion(region, List.of(primary, alternate),
                community, place, new CommunityPlaceBinding(bindings.communityId(), placeId), economy, security, policy,
                sites, affiliations, capabilities, routes, List.of(residents), List.of(),
                new SettlementDevelopment(bindings.communityId(), 25, 0, population,
                        Math.max(0, population - observed.observedPopulation() / 5), 0),
                SettlementDevelopmentPolicy.defaults(bindings.communityId()), authority));
        data.campaignRegions().put(bindings.regionId(), new CampaignRegionRecord(bindings.regionId(),
                definition.id().toString(), definition.version(), displayName(bindings.regionId()),
                observed.dimensionId(), placeId, infrastructureAnchor,
                primaryMineColumn, alternateMineColumn(server, infrastructureAnchor, bindings.regionId()),
                definition.version() >= 2 ? receivingTerminal : null,
                definition.version() >= 2 ? plannedDepot : null, null, null,
                CampaignRegionPresentationStatus.PLANNED, "", 0, -1, -1, 0, 0, "", ""));
        data.setDirty();
    }

    private static int scale(int value, int population, int baselinePopulation) {
        return (value * population + baselinePopulation - 1) / baselinePopulation;
    }

    static boolean productLocationEligible(ServerLevel level, BlockPos anchor) {
        int terrain = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        BlockPos surface = new BlockPos(anchor.getX(), Math.max(level.getMinBuildHeight(), terrain - 1), anchor.getZ());
        return anchor.getY() >= terrain - 16 && anchor.getY() <= terrain + 96
                && level.getFluidState(surface).isEmpty();
    }

    /** Keeps logistics on the settlement-facing ground even when its stable bell is in a tower. */
    static BlockPos infrastructureAnchor(MinecraftServer server, BlockPos landmark, String regionId) {
        BlockPos column = mineColumn(server, landmark, 32, regionId);
        int terrain = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ());
        return new BlockPos(column.getX(), terrain, column.getZ());
    }

    private static BlockPos mineColumn(MinecraftServer server, BlockPos settlement, int distance, String regionId) {
        int direction = direction(server.overworld().getSeed(), regionId);
        int signed = distance;
        return switch (direction) {
            case 0 -> new BlockPos(settlement.getX() + signed, 0, settlement.getZ());
            case 1 -> new BlockPos(settlement.getX() - signed, 0, settlement.getZ());
            case 2 -> new BlockPos(settlement.getX(), 0, settlement.getZ() + signed);
            default -> new BlockPos(settlement.getX(), 0, settlement.getZ() - signed);
        };
    }

    private static BlockPos alternateMineColumn(MinecraftServer server, BlockPos settlement, String regionId) {
        BlockPos midpoint = mineColumn(server, settlement, PRIMARY_MINE_DISTANCE / 2, regionId);
        int direction = direction(server.overworld().getSeed(), regionId);
        return switch (direction) {
            case 0, 1 -> midpoint.offset(0, 0, ALTERNATE_MINE_DISTANCE / 2);
            default -> midpoint.offset(ALTERNATE_MINE_DISTANCE / 2, 0, 0);
        };
    }

    private static BlockPos resolveAnchor(ServerLevel level, BlockPos column) {
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        return new BlockPos(column.getX(), height, column.getZ());
    }

    private static BlockPos receivingTerminal(ServerLevel level, BlockPos infrastructure, BlockPos primaryMine) {
        int dx = Integer.signum(primaryMine.getX() - infrastructure.getX());
        int dz = Integer.signum(primaryMine.getZ() - infrastructure.getZ());
        int x = infrastructure.getX() + dx * 16;
        int z = infrastructure.getZ() + dz * 16;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static BlockPos plannedDepotAnchor(ServerLevel level, BlockPos terminal, BlockPos primaryMine) {
        boolean routeAlongX = primaryMine.getX() != terminal.getX();
        BlockPos deterministicFallback = null;
        for (int distance : new int[] {6, -6, 8, -8, 10, -10}) {
            int x = terminal.getX() + (routeAlongX ? 0 : distance);
            int z = terminal.getZ() + (routeAlongX ? distance : 0);
            BlockPos candidate = new BlockPos(x,
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (deterministicFallback == null) deterministicFallback = candidate;
            if (depotFootprintSafe(level, candidate)) return candidate;
        }
        // Layout planning owns no blocks yet. Pinning a natural-terrain
        // fallback is safe: the executor captures that exact baseline only
        // after the footprint is naturally loaded, before its first write.
        return deterministicFallback;
    }

    private static boolean depotFootprintSafe(ServerLevel level, BlockPos anchor) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos pad = anchor.offset(x, 0, z);
            if (!level.hasChunkAt(pad) || !level.getBlockState(pad.below()).isSolid()
                    || !level.isEmptyBlock(pad) || !level.isEmptyBlock(pad.above())
                    || level.getBlockEntity(pad) != null || level.getBlockEntity(pad.above()) != null) return false;
        }
        return true;
    }

    private static void ensureMine(PaleMirrorSavedData data, ServerLevel level, BlockPos anchor, WorldObjectId id,
                                   java.util.Map<Long, String> baseline) {
        if (data.testMines().containsKey(id)) return;
        TestMineRecord mine = CampaignMineSiteTemplate.isMaterialized(level, anchor)
                ? CampaignMineSiteTemplate.observeExisting(level, anchor, id, StoryAudienceId.globalTestAudience())
                : CampaignMineSiteTemplate.place(level, anchor, id, StoryAudienceId.globalTestAudience(), baseline);
        data.registerTestMine(mine);
    }

    private static int direction(long seed, String regionId) {
        return Math.floorMod((int) (seed ^ (seed >>> 32) ^ regionId.hashCode()), 4);
    }

    private static boolean boundPlace(PaleMirrorSavedData data, WorldObjectId placeId) {
        return data.worldState().livingRegions().stream().anyMatch(region -> region.placeId().equals(placeId));
    }

    private static boolean correctlySpaced(PaleMirrorSavedData data, BlockPos candidate) {
        long required = (long) MIN_REGION_SPACING * MIN_REGION_SPACING;
        return data.campaignRegions().values().stream().allMatch(existing -> {
            long dx = (long) candidate.getX() - existing.settlementAnchor().getX();
            long dz = (long) candidate.getZ() - existing.settlementAnchor().getZ();
            return dx * dx + dz * dz >= required;
        });
    }

    private static String displayName(String regionId) {
        if (IRONHILL_ID.equals(regionId)) return "Ironhill";
        int index = Math.floorMod(regionId.hashCode(), DISPLAY_NAMES.length);
        String key = regionId.substring(regionId.lastIndexOf('_') + 1).toUpperCase(java.util.Locale.ROOT);
        return DISPLAY_NAMES[index] + " " + key.substring(Math.max(0, key.length() - 4));
    }

}
