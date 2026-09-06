package io.farfrontier.palemirror.visuals.genesis;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

record FrontierPalette(BlockState log, BlockState planks, BlockState roof, BlockState foundation,
                       BlockState paving, BlockState pavingSlab, BlockState pavingStairs) {
    static FrontierPalette forClimate(FrontierClimate climate) {
        return switch (climate) {
            case COLD_TAIGA -> new FrontierPalette(Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
                    Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState(),
                    Blocks.COBBLED_DEEPSLATE_STAIRS.defaultBlockState());
            case DRY_ARID -> new FrontierPalette(Blocks.STRIPPED_ACACIA_LOG.defaultBlockState(), Blocks.ACACIA_PLANKS.defaultBlockState(),
                    Blocks.TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
                    Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE_SLAB.defaultBlockState(),
                    Blocks.SANDSTONE_STAIRS.defaultBlockState());
            case TEMPERATE -> new FrontierPalette(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
                    Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
                    Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(),
                    Blocks.STONE_BRICK_STAIRS.defaultBlockState());
        };
    }
}
