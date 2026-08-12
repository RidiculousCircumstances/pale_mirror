package io.farfrontier.palemirror.visuals.genesis;

import com.mojang.serialization.Codec;
import io.farfrontier.palemirror.visuals.runtime.FrontierGenesisRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Writes only the current WorldGenLevel chunk; ServerLevel and neighbour requests are forbidden. */
public final class FrontierWorldgenFeature extends Feature<NoneFeatureConfiguration> {
    public FrontierWorldgenFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        long started = System.nanoTime();
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        CompiledChunkSlice slice = FrontierGenesisRuntime.compiledChunk(chunk.toLong());
        if (slice == null) return false;
        for (CompiledChunkSlice.TerrainColumn column : slice.terrain()) grade(level, column);
        slice.blocks().forEach((position, state) -> setIfDifferent(level, position, state));
        for (CompiledChunkSlice.RailColumn rail : slice.rails()) placeRail(level, rail);
        var current = level.getChunk(chunk.x, chunk.z);
        current.setData(VisualGenesisAttachments.GENESIS_STAMP, slice.stamp());
        current.setUnsaved(true);
        FrontierGenesisRuntime.recordWorldgenDuration(System.nanoTime() - started);
        return true;
    }

    private static void grade(WorldGenLevel level, CompiledChunkSlice.TerrainColumn column) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, column.x(), column.z()) - 1;
        int target = column.targetY();
        if (top > target) {
            for (int y = target + 1; y <= top; y++) setIfDifferent(level, new BlockPos(column.x(), y, column.z()),
                    Blocks.AIR.defaultBlockState());
        } else if (top < target) {
            for (int y = top + 1; y < target; y++) setIfDifferent(level, new BlockPos(column.x(), y, column.z()),
                    Blocks.DIRT.defaultBlockState());
        }
        setIfDifferent(level, new BlockPos(column.x(), target - 1, column.z()), column.foundation());
        setIfDifferent(level, new BlockPos(column.x(), target, column.z()), column.surface());
    }

    private static void placeRail(WorldGenLevel level, CompiledChunkSlice.RailColumn rail) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, rail.rail().getX(), rail.rail().getZ());
        RailEarthwork earthwork = RailEarthwork.resolve(surface, rail.rail().getY());
        if (earthwork == RailEarthwork.GROUND) {
            for (int y = surface; y < rail.rail().getY() - 1; y++) setIfDifferent(level,
                    new BlockPos(rail.rail().getX(), y, rail.rail().getZ()), Blocks.COBBLESTONE.defaultBlockState());
        } else if (earthwork == RailEarthwork.BRIDGE
                && Math.floorMod(rail.rail().getX() + rail.rail().getZ(), 4) == 0) {
            for (int y = surface; y < rail.rail().getY() - 1; y++) setIfDifferent(level,
                    new BlockPos(rail.rail().getX(), y, rail.rail().getZ()), Blocks.OAK_FENCE.defaultBlockState());
        }
        setIfDifferent(level, rail.rail().below(), rail.support());
        for (int y = 0; y < 3; y++) setIfDifferent(level, rail.rail().above(y), Blocks.AIR.defaultBlockState());
        setIfDifferent(level, rail.rail(), rail.railState());
    }

    private static void setIfDifferent(WorldGenLevel level, BlockPos position,
                                       net.minecraft.world.level.block.state.BlockState state) {
        if (!level.getBlockState(position).equals(state)) level.setBlock(position, state, 2);
    }
}
