package io.farfrontier.palemirror.visuals.genesis;

import com.mojang.serialization.Codec;
import io.farfrontier.palemirror.visuals.runtime.FrontierGenesisRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Writes only the current WorldGenLevel chunk; ServerLevel and neighbour requests are forbidden. */
public final class FrontierWorldgenFeature extends Feature<NoneFeatureConfiguration> {
    static final int VEGETATION_CLEANUP_HEIGHT = 32;

    public FrontierWorldgenFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        long started = System.nanoTime();
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        CompiledChunkSlice slice = FrontierGenesisRuntime.compiledChunk(chunk.toLong());
        if (slice == null) return false;
        for (CompiledChunkSlice.TerrainColumn column : slice.terrain()) grade(level, column);
        for (CompiledChunkSlice.VegetationColumn column : slice.vegetation()) clearNaturalVegetation(level, column);
        slice.blocks().forEach((position, state) -> setIfDifferent(level, position, state));
        for (CompiledChunkSlice.RailColumn rail : slice.rails()) placeRail(level, rail);
        var current = level.getChunk(chunk.x, chunk.z);
        current.setData(VisualGenesisAttachments.GENESIS_STAMP, slice.stamp());
        current.setUnsaved(true);
        FrontierGenesisRuntime.recordWorldgenDuration(System.nanoTime() - started);
        return true;
    }

    /** Runs before authored modules, so their intentional gardens and timber remain untouched. */
    static void clearNaturalVegetation(LevelAccessor level, CompiledChunkSlice.VegetationColumn column) {
        int top = Math.min(level.getMaxBuildHeight() - 1,
                level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, column.x(), column.z()) - 1);
        int last = Math.min(top, column.baseY() + VEGETATION_CLEANUP_HEIGHT);
        for (int y = column.baseY() + 1; y <= last; y++) {
            BlockPos position = new BlockPos(column.x(), y, column.z());
            var state = level.getBlockState(position);
            if (naturalVegetation(state)) setIfDifferent(level, position, Blocks.AIR.defaultBlockState());
        }
    }

    static boolean naturalVegetation(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof BushBlock
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.BEEHIVES)
                || state.is(BlockTags.CAVE_VINES)
                || state.is(BlockTags.REPLACEABLE_BY_TREES) && state.getFluidState().isEmpty()
                || state.is(Blocks.COCOA)
                || state.is(Blocks.VINE)
                || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING);
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

    private static void setIfDifferent(LevelAccessor level, BlockPos position,
                                       net.minecraft.world.level.block.state.BlockState state) {
        if (!level.getBlockState(position).equals(state)) level.setBlock(position, state, 2);
    }
}
