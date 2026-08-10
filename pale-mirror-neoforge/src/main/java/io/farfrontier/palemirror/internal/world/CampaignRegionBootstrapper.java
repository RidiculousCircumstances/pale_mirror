package io.farfrontier.palemirror.internal.world;

import java.util.List;
import java.util.Map;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.ResourceStock;
import io.farfrontier.palemirror.domain.RouteState;
import io.farfrontier.palemirror.domain.RouteStatus;
import io.farfrontier.palemirror.domain.SettlementState;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinition;
import io.farfrontier.palemirror.internal.content.CampaignRegionDefinitions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/** Binds the first authored region to a read-only observed settlement; it never builds a village. */
public final class CampaignRegionBootstrapper {
    public static final String IRONHILL_ID = "pale_mirror:ironhill_v1";
    /** Legacy label only; a real region uses {@link LivingRegionState#settlementId()}. */
    public static final WorldObjectId IRONHILL = new WorldObjectId("pale_mirror:ironhill");
    public static final WorldObjectId MINE17 = new WorldObjectId("pale_mirror:mine17");
    public static final WorldObjectId RED_VALLEY = new WorldObjectId("pale_mirror:red_valley_ironworks");
    public static final WorldObjectId MINE17_ROUTE = new WorldObjectId("pale_mirror:mine17_to_ironhill");
    public static final WorldObjectId RED_VALLEY_ROUTE = new WorldObjectId("pale_mirror:red_valley_to_ironhill");
    public static final String RED_VALLEY_DISPATCH = "PM Red Valley Dispatch";
    public static final String IRONHILL_RECEIVING = "PM Ironhill Receiving";
    private static final ResourceLocation IRONHILL_DEFINITION = ResourceLocation.parse(IRONHILL_ID);
    private static final int PRIMARY_MINE_DISTANCE = 640;
    private static final int ALTERNATE_MINE_DISTANCE = 704;
    private static final int MATERIALIZATION_RANGE = 192;

    private CampaignRegionBootstrapper() { }

    public static void tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        ensureCanonicalPlan(server, data, commands);
        CampaignRegionRecord record = data.campaignRegions().get(IRONHILL_ID);
        if (record == null || record.status() == CampaignRegionPresentationStatus.MATERIALIZED
                || record.status() == CampaignRegionPresentationStatus.BLOCKED || !playerIsNearPendingMine(server, record)) return;
        ServerLevel level = server.overworld();
        if (!level.hasChunkAt(record.pendingMineColumn())) return;
        if (!record.pendingMineAnchorResolved()) {
            record.resolvePendingMineAnchor(resolveAnchor(level, record.pendingMineColumn()));
            data.setDirty();
            return;
        }
        if (record.status() == CampaignRegionPresentationStatus.PLANNED) {
            record.startOperation();
            data.setDirty();
            return;
        }
        try {
            if (record.nextOperationIndex() == 0) ensureMine(data, level, record.primaryMineAnchor(), MINE17);
            else if (record.nextOperationIndex() == 1) ensureMine(data, level, record.alternateMineAnchor(), RED_VALLEY);
            else throw new IllegalStateException("Invalid campaign operation index " + record.nextOperationIndex());
            record.completedOperation();
            data.setDirty();
        } catch (IllegalStateException failure) {
            record.block(failure.getMessage());
            data.setDirty();
        }
    }

    private static void ensureCanonicalPlan(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        if (data.worldState().livingRegion(IRONHILL_ID).isPresent()) return;
        SettlementObservationRecord observed = data.settlementObservations().values().stream()
                .filter(value -> value.dimensionId().equals(server.overworld().dimension().location().toString()))
                .sorted(java.util.Comparator.comparing(value -> value.id().value())).findFirst().orElse(null);
        if (observed == null) return;
        CampaignRegionDefinition definition = CampaignRegionDefinitions.require(IRONHILL_DEFINITION);
        WorldObjectId settlementId = observed.id();
        int population = Math.max(1, observed.observedPopulation());
        int ironDemand = Math.max(1, scale(definition.ironDemand(), population, definition.population()));
        int initialStock = Math.max(ironDemand * 4, scale(definition.initialIronStock(), population, definition.population()));
        int capacity = Math.max(initialStock, scale(definition.ironStockCapacity(), population, definition.population()));
        LivingRegionState region = new LivingRegionState(IRONHILL_ID, settlementId, MINE17, RED_VALLEY, MINE17_ROUTE,
                RED_VALLEY_ROUTE, definition.crisisDelaySteps(), null, io.farfrontier.palemirror.domain.LivingRegionStatus.PLANNED, -1);
        FacilityState primary = new FacilityState(MINE17, definition.infectionSource(), definition.ironProduction(), Integer.MAX_VALUE, 0);
        FacilityState alternate = new FacilityState(RED_VALLEY, definition.infectionSource(), definition.ironProduction(), Integer.MAX_VALUE, 0);
        SettlementState settlement = new SettlementState(settlementId, population, definition.defence(),
                Map.of(ResourceKind.IRON, new ResourceStock(capacity, initialStock)), Map.of(ResourceKind.IRON, ironDemand));
        List<RouteState> routes = List.of(
                new RouteState(MINE17_ROUTE, MINE17, settlementId, ResourceKind.IRON, definition.ironProduction(),
                        definition.ironProduction(), RouteStatus.OPERATIONAL),
                new RouteState(RED_VALLEY_ROUTE, RED_VALLEY, settlementId, ResourceKind.IRON, definition.ironProduction(), RouteStatus.PLANNED));
        commands.execute(data.worldState(), new DomainCommand.RegisterLivingRegion(region, List.of(primary, alternate), settlement, routes));
        data.campaignRegions().put(IRONHILL_ID, new CampaignRegionRecord(IRONHILL_ID,
                observed.dimensionId(), settlementId, observed.anchor(), mineColumn(server, observed.anchor(), PRIMARY_MINE_DISTANCE),
                mineColumn(server, observed.anchor(), -ALTERNATE_MINE_DISTANCE), null, null,
                CampaignRegionPresentationStatus.PLANNED, "", 0, -1, -1, 0, 0, "", ""));
        data.setDirty();
    }

    private static int scale(int value, int population, int baselinePopulation) {
        return (value * population + baselinePopulation - 1) / baselinePopulation;
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

    private static boolean playerIsNearPendingMine(MinecraftServer server, CampaignRegionRecord record) {
        BlockPos pending = record.pendingMineColumn();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.serverLevel().dimension().location().toString().equals(record.dimensionId())) continue;
            long dx = player.blockPosition().getX() - pending.getX();
            long dz = player.blockPosition().getZ() - pending.getZ();
            if (dx * dx + dz * dz <= (long) MATERIALIZATION_RANGE * MATERIALIZATION_RANGE) return true;
        }
        return false;
    }

    private static BlockPos resolveAnchor(ServerLevel level, BlockPos column) {
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        return new BlockPos(column.getX(), height + 8, column.getZ());
    }

    private static void ensureMine(PaleMirrorSavedData data, ServerLevel level, BlockPos anchor, WorldObjectId id) {
        if (data.testMines().containsKey(id)) return;
        TestMineRecord mine = TestMineTemplate.isMaterialized(level, anchor)
                ? TestMineTemplate.observeExisting(level, anchor, id, StoryAudienceId.globalTestAudience())
                : TestMineTemplate.place(level, anchor, id, StoryAudienceId.globalTestAudience());
        data.registerTestMine(mine);
    }
}
