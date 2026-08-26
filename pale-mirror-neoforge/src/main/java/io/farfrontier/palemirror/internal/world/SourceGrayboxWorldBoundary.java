package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Immutable dedicated-level identity and finite-border owner for the source graybox. */
final class SourceGrayboxWorldBoundary {
    static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PaleMirrorMod.MOD_ID, "frontier_graybox"));

    private SourceGrayboxWorldBoundary() { }

    /**
     * The graybox never prepares terrain in the ordinary overworld. Its flat
     * data-driven level is a required disposable test boundary, so a missing
     * definition fails activation rather than projecting onto a player landscape.
     */
    static ServerLevel level(MinecraftServer server) {
        ServerLevel result = server.getLevel(DIMENSION);
        if (result == null) throw new IllegalStateException("source graybox dimension is unavailable");
        return result;
    }

    static void enforce(ServerLevel level) {
        level.getWorldBorder().setCenter(0.0d, 0.0d);
        level.getWorldBorder().setSize(ReferenceGrayboxLayout.WORLD_BLOCKS);
    }

    /**
     * Prepares the deliberately neutral operator-entry column before a player
     * crosses dimensions.  This is an explicit, bounded chunk request for the
     * requested entry point, not a materializer request for a source object.
     * The dedicated datapack owns its light-grey footing at Y=63; anything
     * else is a broken graybox boundary and must fail before a player can fall
     * through an unprepared level.
     */
    static BlockPos preparedEntry(ServerLevel level) {
        return preparedEntry(level, new BlockPos(0, ReferenceGrayboxLayout.GROUND_Y - 1, 0));
    }

    static BlockPos preparedEntry(ServerLevel level, BlockPos footing) {
        level.getChunkAt(footing);
        if (!level.getBlockState(footing).isFaceSturdy(level, footing, Direction.UP)) {
            throw new IllegalStateException("source graybox entry footing is unavailable");
        }
        return footing.above();
    }
}
