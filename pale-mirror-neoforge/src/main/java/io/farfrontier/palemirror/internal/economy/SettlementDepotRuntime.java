package io.farfrontier.palemirror.internal.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.SiteAffiliation;
import io.farfrontier.palemirror.domain.SiteAffiliationRole;
import io.farfrontier.palemirror.domain.SiteCapability;
import io.farfrontier.palemirror.domain.SiteCapabilityType;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldSite;
import io.farfrontier.palemirror.domain.WorldSiteType;
import io.farfrontier.palemirror.domain.StructuralIntegrity;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded, provenance-safe PM depot planner/executor. It never loads a chunk. */
public final class SettlementDepotRuntime {
    public static final String TEMPLATE_VERSION = "supply-depot-v1";
    private static final List<BlockPos> OFFSETS = List.of(
            new BlockPos(24, 0, 0), new BlockPos(-24, 0, 0), new BlockPos(0, 0, 24), new BlockPos(0, 0, -24),
            new BlockPos(32, 0, 20), new BlockPos(-32, 0, 20), new BlockPos(32, 0, -20), new BlockPos(-32, 0, -20),
            new BlockPos(20, 0, 32), new BlockPos(-20, 0, 32), new BlockPos(20, 0, -32), new BlockPos(-20, 0, -32),
            new BlockPos(48, 0, 0), new BlockPos(-48, 0, 0), new BlockPos(0, 0, 48), new BlockPos(0, 0, -48));

    private SettlementDepotRuntime() { }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data) {
        boolean changed = false;
        for (var region : data.worldState().livingRegions().stream().sorted(Comparator.comparing(value -> value.id())).toList()) {
            SettlementDepotRecord depot = data.settlementDepots().get(region.communityId());
            if (depot == null) {
                var place = data.worldRegistry().find(region.placeId()).orElse(null);
                if (place == null) continue;
                ServerLevel level = level(server, place.dimensionId());
                if (level == null) continue;
                var presentation = data.campaignRegions().get(region.id());
                BlockPos anchor = presentation != null && presentation.layoutVersion() >= 2
                        ? presentation.depotAnchor()
                        : findAnchor(level, place.anchor(), server.overworld().getSeed() ^ region.communityId().hashCode());
                if (anchor == null) continue;
                WorldObjectId siteId = new WorldObjectId(region.communityId().value() + "_supply_depot");
                depot = new SettlementDepotRecord(siteId, region.communityId(), place.dimensionId(), anchor,
                        captureCells(level, anchor), SettlementDepotState.PLANNED, "");
                data.settlementDepots().put(region.communityId(), depot);
                data.worldState().putSite(new WorldSite(siteId, WorldSiteType.STORAGE, OperationalState.DEGRADED));
                data.worldState().putSiteAffiliation(new SiteAffiliation(siteId, region.communityId(), SiteAffiliationRole.RECIPIENT));
                data.worldState().putSiteCapability(new SiteCapability(siteId, SiteCapabilityType.STORAGE,
                        ResourceKind.IRON, data.worldState().economy(region.communityId()).orElseThrow()
                        .require(ResourceKind.IRON).capacity()));
                changed = true;
                continue;
            }
            ServerLevel level = level(server, depot.dimensionId());
            if (level == null || !level.hasChunkAt(depot.anchor()) || depot.state() == SettlementDepotState.BLOCKED) continue;
            boolean ruined = data.worldState().communityPlaceBinding(depot.communityId())
                    .flatMap(binding -> data.worldState().place(binding.placeId()))
                    .map(place -> place.structuralIntegrity() == StructuralIntegrity.RUINED).orElse(false);
            ruined &= data.worldState().settlementAuthorityProfile(depot.communityId())
                    .map(io.farfrontier.palemirror.domain.SettlementAuthorityProfile::pmRuinAllowed).orElse(true);
            if (depot.state() == SettlementDepotState.ACTIVE && ruined) {
                String failure = materializeRuin(level, depot);
                depot.block(failure == null ? "Settlement place is ruined" : failure);
                data.worldState().site(depot.siteId()).orElseThrow().setOperationalState(OperationalState.OFFLINE);
                changed = true;
                continue;
            }
            if (depot.state() == SettlementDepotState.ACTIVE) continue;
            if (depot.state() == SettlementDepotState.PLANNED) {
                depot.start();
                changed = true;
                continue;
            }
            String failure = materialize(level, depot);
            if (failure == null) {
                depot.activate();
                data.worldState().site(depot.siteId()).orElseThrow().setOperationalState(OperationalState.OPERATIONAL);
            } else depot.block(failure);
            changed = true;
        }
        return changed;
    }

    private static BlockPos findAnchor(ServerLevel level, BlockPos center, long seed) {
        int rotation = Math.floorMod((int) (seed ^ (seed >>> 32)), OFFSETS.size());
        for (int index = 0; index < OFFSETS.size(); index++) {
            BlockPos offset = OFFSETS.get((index + rotation) % OFFSETS.size());
            int x = center.getX() + offset.getX();
            int z = center.getZ() + offset.getZ();
            BlockPos candidate = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (safe(level, candidate)) return candidate;
        }
        return null;
    }

    private static boolean safe(ServerLevel level, BlockPos anchor) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos pad = anchor.offset(x, 0, z);
            if (!level.hasChunkAt(pad) || !level.getBlockState(pad.below()).isSolid()
                    || !level.isEmptyBlock(pad) || !level.isEmptyBlock(pad.above())
                    || level.getBlockEntity(pad) != null || level.getBlockEntity(pad.above()) != null) return false;
        }
        return true;
    }

    private static List<MutableCell> captureCells(ServerLevel level, BlockPos anchor) {
        List<MutableCell> cells = new ArrayList<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) addCell(level, cells, anchor.offset(x, 0, z));
        addCell(level, cells, anchor.above());
        addCell(level, cells, anchor.offset(2, 1, 2));
        return List.copyOf(cells);
    }

    private static void addCell(ServerLevel level, List<MutableCell> cells, BlockPos pos) {
        String baseline = blockId(level, pos);
        cells.add(new MutableCell(pos, baseline, baseline, false));
    }

    private static String materialize(ServerLevel level, SettlementDepotRecord depot) {
        for (MutableCell cell : depot.cells()) {
            String current = blockId(level, cell.position());
            if (cell.conflicted() || !current.equals(cell.baselineBlock()) && !current.equals(cell.lastAppliedBlock())) {
                cell.conflict();
                return "Supply depot cell changed outside Pale Mirror at " + cell.position();
            }
            Block desired = desiredBlock(depot, cell.position());
            String desiredId = BuiltInRegistries.BLOCK.getKey(desired).toString();
            if (!current.equals(desiredId)) level.setBlock(cell.position(), desired.defaultBlockState(), 3);
            if (!blockId(level, cell.position()).equals(desiredId)) return "Supply depot postcondition failed at " + cell.position();
            cell.markApplied(desiredId);
        }
        return null;
    }

    private static String materializeRuin(ServerLevel level, SettlementDepotRecord depot) {
        for (MutableCell cell : depot.cells()) {
            String current = blockId(level, cell.position());
            if (cell.conflicted() || !current.equals(cell.lastAppliedBlock())) {
                cell.conflict();
                return "Ruin overlay conflicts with an unknown depot change at " + cell.position();
            }
            Block desired = cell.position().equals(depot.interactionPosition()) ? Blocks.IRON_BARS
                    : cell.position().equals(depot.anchor().offset(2, 1, 2)) ? Blocks.SOUL_LANTERN
                    : Blocks.CRACKED_STONE_BRICKS;
            String desiredId = BuiltInRegistries.BLOCK.getKey(desired).toString();
            level.setBlock(cell.position(), desired.defaultBlockState(), 3);
            if (!blockId(level, cell.position()).equals(desiredId)) return "Ruin overlay postcondition failed";
            cell.markApplied(desiredId);
        }
        return null;
    }

    public static String upgradeStorehouse(ServerLevel level, SettlementDepotRecord depot) {
        if (depot.state() != SettlementDepotState.ACTIVE) return "Supply depot is not operational";
        for (MutableCell cell : depot.cells()) {
            Block desired = cell.position().equals(depot.interactionPosition()) ? Blocks.BARREL
                    : cell.position().equals(depot.anchor().offset(2, 1, 2)) ? Blocks.LANTERN : Blocks.STONE_BRICKS;
            String desiredId = BuiltInRegistries.BLOCK.getKey(desired).toString();
            String current = blockId(level, cell.position());
            if (cell.conflicted() || !current.equals(cell.lastAppliedBlock()) && !current.equals(desiredId)) {
                cell.conflict();
                return "Storehouse upgrade conflicts with an unknown depot change at " + cell.position();
            }
            if (!current.equals(desiredId)) level.setBlock(cell.position(), desired.defaultBlockState(), 3);
            if (!blockId(level, cell.position()).equals(desiredId)) return "Storehouse upgrade postcondition failed";
            cell.markApplied(desiredId);
        }
        return null;
    }

    private static Block desiredBlock(SettlementDepotRecord depot, BlockPos pos) {
        if (pos.equals(depot.interactionPosition())) return Blocks.BARREL;
        if (pos.equals(depot.anchor().offset(2, 1, 2))) return Blocks.LANTERN;
        return Blocks.POLISHED_ANDESITE;
    }

    private static ServerLevel level(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimensionId)) return level;
        }
        return null;
    }

    private static String blockId(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }
}
