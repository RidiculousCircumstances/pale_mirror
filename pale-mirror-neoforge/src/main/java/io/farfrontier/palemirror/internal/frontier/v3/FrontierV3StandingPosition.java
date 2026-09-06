package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Read-only conversion from a canonical floor anchor to the first place a body may stand.
 *
 * <p>Frontier's {@link BlockPosition} is a semantic floor-cell coordinate: a route carpet,
 * infection overlay, slab or ordinary block belongs at that coordinate, while an entity's feet
 * belong in the first clear cell above it. Exact HOT/COLD hand-off preserves that floor anchor
 * and never chooses another column. Minecraft remains the collision authority after spawn.</p>
 */
final class FrontierV3StandingPosition {
    private static final int MAX_LEGACY_VERTICAL_SEARCH = 8;

    private FrontierV3StandingPosition() { }

    /**
     * Transitional adapter for the historical ambient actor record.
     *
     * <p>That record still carries an untyped position which may be old feet-air data or a
     * support cell. New route, scene and transport code must use {@link #aboveExactFloor}; this
     * method is deliberately kept out of those providers until the actor/ambient schema has one
     * typed body/surface migration. It must never become a new caller's convenience fallback.</p>
     */
    @Deprecated(forRemoval = true)
    static BlockPos aboveFloor(ServerLevel level, BlockPosition anchor) {
        return aboveFloor(level, new BlockPos(anchor.x(), anchor.y(), anchor.z()));
    }

    @Deprecated(forRemoval = true)
    static BlockPos aboveFloor(ServerLevel level, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) return null;
        BlockPos direct = aboveExactFloor(level, anchor);
        if (direct != null) return direct;
        return firstLegacyStandingPosition(level, anchor,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ()));
    }

    /** Resolves only the two-cell-clear position directly above an exact semantic floor cell. */
    static BlockPos aboveExactFloor(ServerLevel level, BlockPosition anchor) {
        return aboveExactFloor(level, new BlockPos(anchor.x(), anchor.y(), anchor.z()));
    }

    static BlockPos aboveExactFloor(ServerLevel level, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) return null;
        BlockPos feet = anchor.above();
        if (!level.hasChunkAt(feet)) return null;
        // A thin route or infection surface is physical geometry.  The retained support is
        // therefore the only legal floor: never climb a player obstruction or consult a
        // heightmap to invent a different world datum for the same canonical cursor.
        return hasPhysicalSupport(level, anchor) && clearExactBodyCell(level, feet)
                && clearExactBodyCell(level, feet.above()) ? feet : null;
    }

    private static BlockPos firstLegacyStandingPosition(ServerLevel level, BlockPos anchor, int startY) {
        for (int y = startY; y <= startY + MAX_LEGACY_VERTICAL_SEARCH; y++) {
            BlockPos candidate = new BlockPos(anchor.getX(), y, anchor.getZ());
            if (!level.hasChunkAt(candidate)) return null;
            if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()
                    && !level.getBlockState(candidate.below()).isAir()) return candidate;
        }
        return null;
    }

    /** The exact canonical body column, without accepting a new floor on top of an obstruction. */
    static boolean hasExactHeadroom(ServerLevel level, BlockPosition anchor) {
        BlockPos floor = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        return level.hasChunkAt(floor) && clearExactBodyCell(level, floor.above()) && clearExactBodyCell(level, floor.above(2));
    }

    /** A retained movement cursor needs its exact physical support as well as two clear body cells. */
    static boolean hasExactStandingColumn(ServerLevel level, BlockPosition anchor) {
        BlockPos floor = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        return level.hasChunkAt(floor) && hasPhysicalSupport(level, floor)
                && clearExactBodyCell(level, floor.above()) && clearExactBodyCell(level, floor.above(2));
    }

    /**
     * The one field-worker exception to ordinary exact standing columns. A canonical crop cell
     * occupies the lower feet voxel above the retained farmland floor, but Vanilla actors may
     * traverse it. This is not a generic obstruction bypass: only a registered harvest scene
     * may request it, and the retained floor and head cell remain exact.
     */
    static BlockPos aboveExactHarvestFieldFloor(ServerLevel level, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) return null;
        BlockPos feet = anchor.above();
        if (!level.hasChunkAt(feet)) return null;
        return !level.getBlockState(anchor).isAir() && clearHarvestWorkerFeetCell(level, feet)
                && clearExactBodyCell(level, feet.above()) ? feet : null;
    }

    static boolean hasExactHarvestFieldStandingColumn(ServerLevel level, BlockPosition anchor) {
        BlockPos floor = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        return level.hasChunkAt(floor) && !level.getBlockState(floor).isAir()
                && clearHarvestWorkerFeetCell(level, floor.above()) && clearExactBodyCell(level, floor.above(2));
    }

    /**
     * An infection/route overlay is a thin physical surface, not a substitute floor or a wall.
     * It may occupy the retained feet cell while an entity takes the matching bounded vertical
     * collision step.  Anything taller remains an exact-edge obstruction; this never accepts a
     * player block by climbing it or by choosing a neighbouring column.
     */
    private static boolean clearExactBodyCell(ServerLevel level, BlockPos position) {
        if (level.getBlockState(position).isAir()) return true;
        VoxelShape collision = level.getBlockState(position).getCollisionShape(level, position);
        return !collision.isEmpty() && collision.max(Direction.Axis.Y) <= 0.125D;
    }

    /** A non-air decorative or pass-through block (notably a crop) is never a body support. */
    private static boolean hasPhysicalSupport(ServerLevel level, BlockPos position) {
        VoxelShape collision = level.getBlockState(position).getCollisionShape(level, position);
        return !collision.isEmpty();
    }

    private static boolean clearHarvestWorkerFeetCell(ServerLevel level, BlockPos position) {
        return clearExactBodyCell(level, position) || level.getBlockState(position).getBlock() instanceof CropBlock;
    }
}
