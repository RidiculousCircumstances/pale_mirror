package io.farfrontier.palemirror.visuals.genesis;

import com.mojang.serialization.Codec;
import io.farfrontier.palemirror.visuals.runtime.FrontierGenesisRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
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
    static final int VEGETATION_BASE_MARGIN = 32;

    public FrontierWorldgenFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        long started = System.nanoTime();
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        CompiledChunkSlice slice = FrontierGenesisRuntime.compiledChunk(chunk.toLong());
        if (slice == null) return false;
        for (CompiledChunkSlice.VegetationColumn column : slice.vegetation()) clearNaturalVegetation(level, column);
        for (CompiledChunkSlice.TerrainColumn column : slice.terrain()) grade(level, column);
        // Heightmaps are updated by grading and by other late worldgen
        // features.  A second pass is therefore required before authored
        // blocks are written: it catches crowns and trunks whose original
        // WORLD_SURFACE_WG top was stale without touching intentional timber,
        // hedges or planters from the PM modules that follow.
        for (CompiledChunkSlice.VegetationColumn column : slice.vegetation()) clearNaturalVegetation(level, column);
        slice.blocks().forEach((position, state) -> setIfDifferent(level, position, state));
        slice.decorations().forEach(value -> setIfDifferent(level, value.position(), value.state()));
        for (CompiledChunkSlice.RailColumn rail : slice.rails()) placeRail(level, rail);
        var current = level.getChunk(chunk.x, chunk.z);
        current.setData(VisualGenesisAttachments.GENESIS_STAMP, slice.stamp());
        current.setData(VisualGenesisAttachments.FINALIZATION_STAMP, slice.stamp());
        current.setUnsaved(true);
        FrontierGenesisRuntime.recordWorldgenDuration(System.nanoTime() - started);
        return true;
    }

    /** Runs before authored modules, so their intentional gardens and timber remain untouched. */
    static void clearNaturalVegetation(LevelAccessor level, CompiledChunkSlice.VegetationColumn column) {
        // This pass deliberately runs before grading. Capture the original top
        // so arbitrarily tall modded crowns, hanging vines and bee nests cannot
        // survive above a lowered authored surface. Start below every accepted
        // settlement relief band so trunks rooted below the planned datum are
        // removed as well.
        int last = Math.min(level.getMaxBuildHeight() - 1,
                level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, column.x(), column.z()) - 1);
        int first = Math.max(level.getMinBuildHeight(), column.baseY() - VEGETATION_BASE_MARGIN);
        for (int y = first; y <= last; y++) {
            BlockPos position = new BlockPos(column.x(), y, column.z());
            var state = level.getBlockState(position);
            if (naturalVegetation(state)) setIfDifferent(level, position, Blocks.AIR.defaultBlockState());
        }
    }


    static boolean naturalVegetation(net.minecraft.world.level.block.state.BlockState state) {
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        boolean semanticPlant = path.endsWith("_leaves") || path.endsWith("_leaf")
                || path.endsWith("_sapling") || path.endsWith("_flower")
                || path.endsWith("_flowers") || path.endsWith("_vine")
                || path.endsWith("_mushroom_block") || path.equals("mushroom_stem")
                || path.contains("foliage") || path.contains("bee_nest") || path.contains("beehive");
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
                || state.is(Blocks.COBWEB)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING)
                || semanticPlant;
    }

    private static void grade(WorldGenLevel level, CompiledChunkSlice.TerrainColumn column) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, column.x(), column.z()) - 1;
        int target = resolvedTarget(top, column.targetY(), column.blendDistance());
        if (column.blendDistance() > 0 && target == top) return;
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

    static int resolvedTarget(int naturalTop, int authoredTarget, int blendDistance) {
        if (blendDistance == 0) return authoredTarget;
        return Math.max(authoredTarget - blendDistance,
                Math.min(authoredTarget + blendDistance, naturalTop));
    }


    private static void placeRail(LevelAccessor level, CompiledChunkSlice.RailColumn rail) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, rail.rail().getX(), rail.rail().getZ());
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, rail.rail().getX(), rail.rail().getZ());
        placeRailColumn(level, rail, surface, floor);
    }

    static void placeRailColumn(LevelAccessor level, CompiledChunkSlice.RailColumn rail,
                                int surface, int floor) {
        boolean water = surface > floor + 1
                || !level.getFluidState(new BlockPos(rail.rail().getX(), surface - 1, rail.rail().getZ())).isEmpty();
        RailEarthwork earthwork = water ? RailEarthwork.BRIDGE : RailEarthwork.resolve(surface, rail.rail().getY());
        if (earthwork == RailEarthwork.GROUND) {
            for (int y = surface; y < rail.rail().getY() - 1; y++) setIfDifferent(level,
                    new BlockPos(rail.rail().getX(), y, rail.rail().getZ()), Blocks.COBBLESTONE.defaultBlockState());
        } else if (earthwork == RailEarthwork.BRIDGE && rail.supportPier()) {
            int base = water ? floor : surface;
            for (int y = base; y < rail.rail().getY() - 1; y++) setIfDifferent(level,
                    new BlockPos(rail.rail().getX(), y, rail.rail().getZ()),
                    water ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.STRIPPED_OAK_LOG.defaultBlockState());
        }
        setIfDifferent(level, rail.rail().below(), water
                ? Blocks.OAK_PLANKS.defaultBlockState() : rail.support());
        for (int y = 0; y < 3; y++) setIfDifferent(level, rail.rail().above(y), Blocks.AIR.defaultBlockState());
        setIfDifferent(level, rail.rail(), rail.railState());
    }

    private static void setIfDifferent(LevelAccessor level, BlockPos position,
                                       net.minecraft.world.level.block.state.BlockState state) {
        if (!level.getBlockState(position).equals(state)) level.setBlock(position, state, 2);
    }
}
