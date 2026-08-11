package io.farfrontier.palemirror.internal.world;

import java.util.List;
import io.farfrontier.palemirror.domain.CommunityPlaceBinding;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
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
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

/** Binds the first authored region to a read-only observed settlement; it never builds a village. */
public final class CampaignRegionBootstrapper {
    public static final String IRONHILL_ID = "pale_mirror:ironhill_v2";
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
    private static final ResourceLocation IRONHILL_DEFINITION = ResourceLocation.parse(IRONHILL_ID);
    private static final int PRIMARY_MINE_DISTANCE = 640;
    private static final int ALTERNATE_MINE_DISTANCE = 704;
    private static final int MIN_SETTLEMENT_DISTANCE = 800;
    private static final int MAX_SETTLEMENT_DISTANCE = 2_000;
    private static final int SITE_TICKET_RADIUS = 4;
    private static final TicketType<BlockPos> SITE_COMMISSIONING_TICKET = TicketType.create(
            "pale_mirror_site_commissioning", java.util.Comparator.comparingLong(BlockPos::asLong), 100);
    private static final java.util.Map<ServerLevel, java.util.Map<String, BlockPos>> ACTIVE_TICKETS =
            new java.util.IdentityHashMap<>();

    private CampaignRegionBootstrapper() { }

    public static void tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        tick(server, data, commands, true);
    }

    public static void tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands,
                            boolean automaticBinding) {
        if (automaticBinding) ensureCanonicalPlan(server, data, commands);
        CampaignRegionRecord record = data.campaignRegions().get(IRONHILL_ID);
        if (record == null) {
            releaseTicket(server.overworld(), IRONHILL_ID);
            return;
        }
        advancePhysicalPlan(server.overworld(), data, record);
    }

    /** Advances persisted PM-authored site work without requiring a player near either planned mine. */
    public static void advancePhysicalPlan(ServerLevel level, PaleMirrorSavedData data, CampaignRegionRecord record) {
        if (record.status() == CampaignRegionPresentationStatus.MATERIALIZED) {
            releaseTicket(level, record.id());
            return;
        }
        maintainTicket(level, record.id(), CampaignMineSiteTemplate.commissioningTicketAnchor(record.pendingMineColumn()));
        if (!level.hasChunkAt(record.pendingMineColumn())) return;
        if (record.status() == CampaignRegionPresentationStatus.BLOCKED) {
            if (record.pendingMineAnchorResolved()
                    && record.diagnostic().startsWith("MineSite changed after planning at ")) {
                record.retryBlockedFirstGenerationExecution();
                data.setDirty();
            } else {
                if (record.pendingMineAnchorResolved() || !record.diagnostic().startsWith("MineSite conflict at ")) {
                    releaseTicket(level, record.id());
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
                    releaseTicket(level, record.id());
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
                releaseTicket(level, record.id());
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
            if (record.nextOperationIndex() == 0) ensureMine(data, level, record.primaryMineAnchor(), MINE17, record.pendingMineBaseline());
            else if (record.nextOperationIndex() == 1) ensureMine(data, level, record.alternateMineAnchor(), RED_VALLEY, record.pendingMineBaseline());
            else throw new IllegalStateException("Invalid campaign operation index " + record.nextOperationIndex());
            record.completedOperation();
            if (record.status() == CampaignRegionPresentationStatus.MATERIALIZED) releaseTicket(level, record.id());
            data.setDirty();
        } catch (IllegalStateException failure) {
            record.block(failure.getMessage());
            releaseTicket(level, record.id());
            data.setDirty();
        }
    }

    public static void stop(MinecraftServer server) {
        ServerLevel level = server.overworld();
        java.util.Map<String, BlockPos> tickets = ACTIVE_TICKETS.remove(level);
        if (tickets != null) tickets.forEach((id, position) -> level.getChunkSource().removeRegionTicket(
                SITE_COMMISSIONING_TICKET, new ChunkPos(position), SITE_TICKET_RADIUS, position));
    }

    private static void ensureCanonicalPlan(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        if (data.worldState().livingRegion(IRONHILL_ID).isPresent()) return;
        long gameTime = server.overworld().getGameTime();
        SettlementObservationRecord observed = data.settlementObservations().values().stream()
                .filter(value -> value.dimensionId().equals(server.overworld().dimension().location().toString()))
                .filter(SettlementObservationRecord::strongEnoughForRecognition)
                .filter(value -> value.freshness(gameTime) == ObservationFreshness.CURRENT)
                .filter(AdapterRegistry::campaignEligible)
                .filter(value -> productLocationEligible(server.overworld(), value.anchor()))
                .sorted(java.util.Comparator.comparing(value -> value.id().value())).findFirst().orElse(null);
        if (observed == null) return;
        registerCanonicalPlan(server, data, commands, observed);
    }

    /** Explicit operator selection still uses the exact production registration pipeline. */
    public static void bindCandidate(MinecraftServer server, PaleMirrorSavedData data,
                                     DomainCommandProcessor commands, WorldObjectId candidateId) {
        if (data.worldState().livingRegion(IRONHILL_ID).isPresent()) {
            throw new IllegalStateException("A living region is already bound; inspect it or use the safe reset workflow");
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
        registerCanonicalPlan(server, data, commands, observed);
    }

    private static void registerCanonicalPlan(MinecraftServer server, PaleMirrorSavedData data,
                                              DomainCommandProcessor commands, SettlementObservationRecord observed) {
        CampaignRegionDefinition definition = CampaignRegionDefinitions.require(IRONHILL_DEFINITION);
        WorldObjectId placeId = observed.id();
        int population = Math.max(1, observed.observedPopulation());
        int ironDemand = Math.max(1, scale(definition.ironDemand(), population, definition.population()));
        int initialStock = Math.max(ironDemand * 4, scale(definition.initialIronStock(), population, definition.population()));
        int capacity = Math.max(initialStock, scale(definition.ironStockCapacity(), population, definition.population()));
        LivingRegionState region = new LivingRegionState(IRONHILL_ID, IRONHILL, placeId, MINE17, RED_VALLEY,
                MINE17_ROUTE, RED_VALLEY_ROUTE, definition.crisisDelaySteps(), null, RecognitionState.DISCOVERED, -1);
        FacilityState primary = new FacilityState(MINE17, definition.infectionSource(), definition.ironProduction(), Integer.MAX_VALUE, 0);
        FacilityState alternate = new FacilityState(RED_VALLEY, definition.infectionSource(), definition.ironProduction(), Integer.MAX_VALUE, 0);
        SettlementCommunity community = new SettlementCommunity(IRONHILL);
        int guards = Math.min(population, observed.registeredGuards());
        PopulationGroup residents = PopulationGroup.residents("pale_mirror:ironhill_residents", IRONHILL, placeId,
                java.util.Map.of(SettlementCohort.CIVILIANS, population - guards, SettlementCohort.GUARDS, guards));
        SettlementPlace place = new SettlementPlace(placeId);
        int rationedDemand = Math.min(ironDemand, Math.max(0,
                scale(definition.rationedIronDemand(), population, definition.population())));
        SettlementEconomy economy = new SettlementEconomy(IRONHILL, java.util.Map.of(ResourceKind.IRON,
                new ResourceAccount(capacity, initialStock, 0, ironDemand, rationedDemand)));
        SettlementSecurity security = new SettlementSecurity(IRONHILL, definition.defence(), definition.defence(),
                observed.registeredGuards(), observed.registeredGuards() > 0 ? GuardCapability.PRESENT : GuardCapability.ABSENT);
        SettlementPolicy policy = new SettlementPolicy(IRONHILL, definition.rationReserveSteps(),
                definition.requestReserveSteps(), definition.defenceLossPerUnavailableStep(), definition.stableStepsToRecover(),
                definition.evacuationDefenceThreshold(), definition.emergencyGraceSteps(), definition.evacuationDurationSteps());
        List<WorldSite> sites = List.of(
                new WorldSite(MINE17_DISPATCH_SITE, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                new WorldSite(RED_VALLEY_DISPATCH_SITE, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL),
                new WorldSite(IRONHILL_RECEIVING_SITE, WorldSiteType.LOGISTICS_ENDPOINT, OperationalState.OPERATIONAL));
        List<SiteAffiliation> affiliations = List.of(
                new SiteAffiliation(MINE17_DISPATCH_SITE, MINE17, SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(RED_VALLEY_DISPATCH_SITE, RED_VALLEY, SiteAffiliationRole.SUPPLIER),
                new SiteAffiliation(IRONHILL_RECEIVING_SITE, IRONHILL, SiteAffiliationRole.RECIPIENT));
        List<SiteCapability> capabilities = List.of(
                new SiteCapability(MINE17_DISPATCH_SITE, SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()),
                new SiteCapability(RED_VALLEY_DISPATCH_SITE, SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()),
                new SiteCapability(IRONHILL_RECEIVING_SITE, SiteCapabilityType.LOGISTICS, ResourceKind.IRON, definition.ironProduction()));
        List<RouteContract> routes = List.of(
                new RouteContract(MINE17_ROUTE, MINE17_DISPATCH_SITE, IRONHILL_RECEIVING_SITE, RouteProvider.MANAGED_RAILWAY,
                        ResourceKind.IRON, definition.ironProduction(), definition.routeCurrentWindowSteps(),
                        definition.routeExpiryWindowSteps(), RouteContractStatus.PLANNED),
                new RouteContract(RED_VALLEY_ROUTE, RED_VALLEY_DISPATCH_SITE, IRONHILL_RECEIVING_SITE, RouteProvider.CREATE,
                        ResourceKind.IRON, definition.ironProduction(), definition.routeCurrentWindowSteps(),
                        definition.routeExpiryWindowSteps(), RouteContractStatus.PLANNED));
        commands.execute(data.worldState(), new DomainCommand.RegisterLivingRegion(region, List.of(primary, alternate),
                community, place, new CommunityPlaceBinding(IRONHILL, placeId), economy, security, policy, sites, affiliations,
                capabilities, routes, List.of(residents)));
        SettlementAuthorityProfile authority = "pale_mirror:native_reconciled".equals(observed.authorityProfileId())
                ? SettlementAuthorityProfile.nativeReconciled(IRONHILL)
                : SettlementAuthorityProfile.pmManaged(IRONHILL);
        commands.execute(data.worldState(), new DomainCommand.RegisterSettlementAuthorityProfile(authority));
        data.worldState().putSettlementDevelopment(new SettlementDevelopment(IRONHILL, 25, 0,
                population, Math.max(0, population - observed.observedPopulation() / 5), 0));
        data.worldState().putDevelopmentPolicy(SettlementDevelopmentPolicy.defaults(IRONHILL));
        BlockPos infrastructureAnchor = infrastructureAnchor(server, observed.anchor());
        data.campaignRegions().put(IRONHILL_ID, new CampaignRegionRecord(IRONHILL_ID,
                observed.dimensionId(), placeId, infrastructureAnchor,
                mineColumn(server, infrastructureAnchor, PRIMARY_MINE_DISTANCE),
                alternateMineColumn(server, infrastructureAnchor), null, null,
                CampaignRegionPresentationStatus.PLANNED, "", 0, -1, -1, 0, 0, "", ""));
        data.setDirty();
    }

    private static int scale(int value, int population, int baselinePopulation) {
        return (value * population + baselinePopulation - 1) / baselinePopulation;
    }

    static boolean productLocationEligible(ServerLevel level, BlockPos anchor) {
        BlockPos spawn = level.getSharedSpawnPos();
        long dx = (long) anchor.getX() - spawn.getX(); long dz = (long) anchor.getZ() - spawn.getZ();
        long distanceSquared = dx * dx + dz * dz;
        if (distanceSquared < (long) MIN_SETTLEMENT_DISTANCE * MIN_SETTLEMENT_DISTANCE
                || distanceSquared > (long) MAX_SETTLEMENT_DISTANCE * MAX_SETTLEMENT_DISTANCE) return false;
        int terrain = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        BlockPos surface = new BlockPos(anchor.getX(), Math.max(level.getMinBuildHeight(), terrain - 1), anchor.getZ());
        return anchor.getY() >= terrain - 16 && anchor.getY() <= terrain + 96
                && level.getFluidState(surface).isEmpty();
    }

    /** Keeps logistics on the settlement-facing ground even when its stable bell is in a tower. */
    static BlockPos infrastructureAnchor(MinecraftServer server, BlockPos landmark) {
        BlockPos column = mineColumn(server, landmark, 32);
        int terrain = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ());
        return new BlockPos(column.getX(), terrain, column.getZ());
    }

    private static BlockPos mineColumn(MinecraftServer server, BlockPos settlement, int distance) {
        int direction = Math.floorMod((int) (server.overworld().getSeed() ^ (server.overworld().getSeed() >>> 32)), 4);
        int signed = distance;
        return switch (direction) {
            case 0 -> new BlockPos(settlement.getX() + signed, 0, settlement.getZ());
            case 1 -> new BlockPos(settlement.getX() - signed, 0, settlement.getZ());
            case 2 -> new BlockPos(settlement.getX(), 0, settlement.getZ() + signed);
            default -> new BlockPos(settlement.getX(), 0, settlement.getZ() - signed);
        };
    }

    private static BlockPos alternateMineColumn(MinecraftServer server, BlockPos settlement) {
        BlockPos midpoint = mineColumn(server, settlement, PRIMARY_MINE_DISTANCE / 2);
        int direction = Math.floorMod((int) (server.overworld().getSeed() ^ (server.overworld().getSeed() >>> 32)), 4);
        return switch (direction) {
            case 0, 1 -> midpoint.offset(0, 0, ALTERNATE_MINE_DISTANCE / 2);
            default -> midpoint.offset(ALTERNATE_MINE_DISTANCE / 2, 0, 0);
        };
    }

    private static BlockPos resolveAnchor(ServerLevel level, BlockPos column) {
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        return new BlockPos(column.getX(), height, column.getZ());
    }

    private static void ensureMine(PaleMirrorSavedData data, ServerLevel level, BlockPos anchor, WorldObjectId id,
                                   java.util.Map<Long, String> baseline) {
        if (data.testMines().containsKey(id)) return;
        TestMineRecord mine = CampaignMineSiteTemplate.isMaterialized(level, anchor)
                ? CampaignMineSiteTemplate.observeExisting(level, anchor, id, StoryAudienceId.globalTestAudience())
                : CampaignMineSiteTemplate.place(level, anchor, id, StoryAudienceId.globalTestAudience(), baseline);
        data.registerTestMine(mine);
    }

    private static void maintainTicket(ServerLevel level, String regionId, BlockPos position) {
        java.util.Map<String, BlockPos> tickets = ACTIVE_TICKETS.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
        BlockPos next = position.immutable();
        BlockPos previous = tickets.put(regionId, next);
        if (next.equals(previous)) return;
        if (previous != null) level.getChunkSource().removeRegionTicket(
                SITE_COMMISSIONING_TICKET, new ChunkPos(previous), SITE_TICKET_RADIUS, previous);
        level.getChunkSource().addRegionTicket(
                SITE_COMMISSIONING_TICKET, new ChunkPos(next), SITE_TICKET_RADIUS, next);
    }

    private static void releaseTicket(ServerLevel level, String regionId) {
        java.util.Map<String, BlockPos> tickets = ACTIVE_TICKETS.get(level);
        BlockPos previous = tickets == null ? null : tickets.remove(regionId);
        if (previous != null) level.getChunkSource().removeRegionTicket(
                SITE_COMMISSIONING_TICKET, new ChunkPos(previous), SITE_TICKET_RADIUS, previous);
        if (tickets != null && tickets.isEmpty()) ACTIVE_TICKETS.remove(level);
    }
}
