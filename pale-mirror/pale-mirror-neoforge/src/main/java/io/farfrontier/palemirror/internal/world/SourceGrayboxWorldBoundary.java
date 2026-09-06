package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PhysicalWorld;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable dedicated-level identity and finite-border owner for the source graybox. */
final class SourceGrayboxWorldBoundary {
    static final ResourceKey<Level> DIMENSION = FrontierV3PhysicalWorld.DIMENSION;
    private static final int OBSERVATION_DECK_RADIUS = 3;
    private static final int OBSERVATION_DECK_X = 8;
    private static final int OBSERVATION_DECK_Z = ReferenceGrayboxLayout.MIN_Z - 56;
    private static final BlockState NEUTRAL_FOOTING = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState DECK_FOOTING = Blocks.YELLOW_CONCRETE.defaultBlockState();
    private static final BlockState DECK_LANTERN = Blocks.SEA_LANTERN.defaultBlockState();

    private SourceGrayboxWorldBoundary() { }

    /**
     * The graybox never prepares terrain in the ordinary overworld. Its flat
     * data-driven level is a required disposable test boundary, so a missing
     * definition fails activation rather than projecting onto a player landscape.
     */
    static ServerLevel level(MinecraftServer server) {
        try {
            return FrontierV3PhysicalWorld.require(server);
        } catch (IllegalStateException missing) {
            throw new IllegalStateException("source graybox dimension is unavailable", missing);
        }
    }

    static void enforce(ServerLevel level) {
        level.getWorldBorder().setCenter(0.0d, 0.0d);
        level.getWorldBorder().setSize(ReferenceGrayboxLayout.WORLD_BLOCKS);
    }

    /**
     * Prepares the fixed operator observation deck before a player crosses
     * dimensions.  The deck is inside the physical border but outside the
     * logical 64x44 arena, so no source resident, bioform, or projected claim
     * can occupy its entry cell.  It is a bounded physical boundary owned by
     * this class, not a materializer request for a source object.
     */
    static BlockPos preparedEntry(ServerLevel level) {
        BlockPos footing = observationDeckFooting();
        prepareObservationDeck(level, footing);
        return preparedEntry(level, footing);
    }

    static BlockPos preparedEntry(ServerLevel level, BlockPos footing) {
        level.getChunkAt(footing);
        if (!level.getBlockState(footing).isFaceSturdy(level, footing, Direction.UP)) {
            throw new IllegalStateException("source graybox entry footing is unavailable");
        }
        return footing.above();
    }

    static BlockPos observationDeckFooting() {
        return new BlockPos(OBSERVATION_DECK_X, ReferenceGrayboxLayout.GROUND_Y - 1, OBSERVATION_DECK_Z);
    }

    static boolean isNeutralObservationDeck(BlockPos position) {
        return position.getX() >= ReferenceGrayboxLayout.MIN_X
                && position.getX() < ReferenceGrayboxLayout.MAX_X_EXCLUSIVE
                && position.getZ() >= -ReferenceGrayboxLayout.WORLD_BLOCKS / 2
                && position.getZ() < ReferenceGrayboxLayout.MIN_Z;
    }

    private static void prepareObservationDeck(ServerLevel level, BlockPos footing) {
        if (!isNeutralObservationDeck(footing)) {
            throw new IllegalStateException("source graybox observation deck is outside the neutral border");
        }
        Map<BlockPos, BlockState> prior = new LinkedHashMap<>();
        for (int x = -OBSERVATION_DECK_RADIUS; x <= OBSERVATION_DECK_RADIUS; x++) {
            for (int z = -OBSERVATION_DECK_RADIUS; z <= OBSERVATION_DECK_RADIUS; z++) {
                BlockPos position = footing.offset(x, 0, z);
                level.getChunkAt(position);
                BlockState actual = level.getBlockState(position);
                BlockState desired = x == 0 && z == 0 ? DECK_LANTERN : DECK_FOOTING;
                if (!actual.equals(NEUTRAL_FOOTING) && !actual.equals(desired)) {
                    throw new IllegalStateException("source graybox operator deck conflicts with a non-boundary block");
                }
                prior.put(position, actual);
            }
        }
        BlockPos entry = footing.above();
        if (!level.getBlockState(entry).isAir()) {
            throw new IllegalStateException("source graybox operator entry is obstructed");
        }
        for (Map.Entry<BlockPos, BlockState> entryBlock : prior.entrySet()) {
            BlockState desired = entryBlock.getKey().equals(footing) ? DECK_LANTERN : DECK_FOOTING;
            if (entryBlock.getValue().equals(desired)) continue;
            if (level.setBlock(entryBlock.getKey(), desired, 3)) continue;
            prior.forEach((position, previous) -> level.setBlock(position, previous, 3));
            throw new IllegalStateException("source graybox operator deck could not be prepared");
        }
    }
}
