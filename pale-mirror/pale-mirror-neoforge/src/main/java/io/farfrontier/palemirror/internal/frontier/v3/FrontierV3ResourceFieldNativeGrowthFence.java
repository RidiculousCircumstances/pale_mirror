package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Same-event undo of forced vanilla growth on one exactly observed cell-owned crop. */
final class FrontierV3ResourceFieldNativeGrowthFence {
    private static final int MAX_PRE_OBSERVATIONS_PER_TICK = 8_192;
    private static final Map<ServerLevel, Window> WINDOWS = new WeakHashMap<>();

    private FrontierV3ResourceFieldNativeGrowthFence() { }

    /** The claimed cell is blocked even if drift prevents an exact undo snapshot. */
    static boolean block(ServerLevel level, ResourceSite site,
                         FrontierV3ResourceSiteLedger.FieldClaim field,
                         ResourceFieldLayout.CellId cellId) {
        Objects.requireNonNull(level, "native growth world");
        Objects.requireNonNull(site, "native growth site");
        Objects.requireNonNull(field, "native growth cell owner");
        ResourceFieldLayout.Cell cell = site.layout().requireCell(cellId);
        if (!field.siteId().equals(site.id())) throw new IllegalArgumentException("native growth has a foreign field owner");
        if (!(field instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || !owner.witness().matchesLayout(site.id(), site.layout())) return true;
        var reading = FrontierV3ResourceFieldObservation.read(level, cell, "native-growth-pre");
        if (!(reading instanceof FrontierV3ResourceFieldObservation.Owned owned)
                || !owned.condition().equals(owner.witness().cell(cellId).committed())) return true;
        BlockPos position = new BlockPos(cell.crop().x(), cell.crop().y(), cell.crop().z());
        BlockState before = level.getBlockState(position);
        if (!before.is(Blocks.WHEAT) || before.getValue(CropBlock.AGE) >= CropBlock.MAX_AGE) return true;
        Window window = WINDOWS.computeIfAbsent(level, ignored -> new Window());
        if (window.tick != level.getGameTime()) {
            window.tick = level.getGameTime();
            window.snapshots.clear();
        }
        if (window.snapshots.size() < MAX_PRE_OBSERVATIONS_PER_TICK || window.snapshots.containsKey(position))
            window.snapshots.put(position.immutable(), new Snapshot(site.id(), cellId, owner, before));
        return true;
    }

    /** Restore only the immediately forced wheat-age increment from the same retained owner. */
    static boolean restore(ServerLevel level, ResourceSite site,
                           FrontierV3ResourceSiteLedger.FieldClaim field, BlockPos position) {
        Objects.requireNonNull(level, "native growth world");
        Objects.requireNonNull(site, "native growth site");
        Objects.requireNonNull(field, "native growth cell owner");
        Objects.requireNonNull(position, "native growth crop");
        Window window = WINDOWS.get(level);
        if (window == null || window.tick != level.getGameTime()) return false;
        Snapshot before = window.snapshots.remove(position);
        if (before == null || !(field instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner != before.owner || !before.siteId.equals(site.id())
                || !owner.witness().matchesLayout(site.id(), site.layout())
                || !site.layout().cropAt(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(
                        position.getX(), position.getY(), position.getZ()))
                        .map(cell -> cell.id().equals(before.cellId)).orElse(false)) return false;
        BlockState current = level.getBlockState(position);
        if (!current.is(Blocks.WHEAT)
                || current.getValue(CropBlock.AGE) != before.block.getValue(CropBlock.AGE) + 1) return false;
        ResourceFieldLayout.Cell geometry = site.layout().requireCell(before.cellId);
        if (!(FrontierV3ResourceFieldObservation.read(level, geometry, "native-growth-post")
                instanceof FrontierV3ResourceFieldObservation.Owned)) return false;
        if (!level.setBlock(position, before.block, 2)) return false;
        return level.getBlockState(position).equals(before.block);
    }

    private static final class Window {
        private long tick = Long.MIN_VALUE;
        private final Map<BlockPos, Snapshot> snapshots = new HashMap<>();
    }

    private record Snapshot(SubjectId siteId, ResourceFieldLayout.CellId cellId,
                            FrontierV3ResourceSiteLedger.FieldOwnership owner, BlockState block) { }
}
