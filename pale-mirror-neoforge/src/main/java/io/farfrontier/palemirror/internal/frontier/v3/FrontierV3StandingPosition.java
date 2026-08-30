package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Read-only conversion from a canonical floor anchor to the first place a body may stand.
 *
 * <p>Frontier's {@link BlockPosition} is a semantic floor-cell coordinate: a route carpet,
 * infection overlay, slab or ordinary block belongs at that coordinate, while an entity's feet
 * belong in the first clear cell above it. Exact HOT/COLD hand-off preserves that floor anchor
 * and never chooses another column. Minecraft remains the collision authority after spawn.</p>
 */
final class FrontierV3StandingPosition {
    private static final int MAX_VERTICAL_SEARCH = 8;

    private FrontierV3StandingPosition() { }

    static BlockPos aboveFloor(ServerLevel level, BlockPosition anchor) {
        return aboveFloor(level, new BlockPos(anchor.x(), anchor.y(), anchor.z()));
    }

    static BlockPos aboveFloor(ServerLevel level, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) return null;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        for (int y = surface; y <= surface + MAX_VERTICAL_SEARCH; y++) {
            BlockPos candidate = new BlockPos(anchor.getX(), y, anchor.getZ());
            if (!level.hasChunkAt(candidate)) return null;
            // A thin route or infection surface is not a full sturdy face, but it is still
            // physical world geometry. Never shift, clear or manufacture a different floor.
            if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()
                    && !level.getBlockState(candidate.below()).isAir()) return candidate;
        }
        return null;
    }
}
