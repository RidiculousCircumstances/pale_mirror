package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Exact engine representation of owned cultivable soils; grass is not silently rewritten as dirt. */
final class FrontierV3FieldBlocks {
    static ResourceFieldCycle.Soil soil(BlockState block) {
        if (block.is(Blocks.FARMLAND)) return ResourceFieldCycle.Soil.FARMLAND;
        if (block.is(Blocks.DIRT)) return ResourceFieldCycle.Soil.DIRT;
        if (block.is(Blocks.GRASS_BLOCK)) return ResourceFieldCycle.Soil.GRASS;
        return null;
    }
    static BlockState soil(ResourceFieldCycle.Soil value) {
        return switch (value) {
            case FARMLAND -> Blocks.FARMLAND.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            default -> throw new IllegalArgumentException("unobserved or foreign soil has no owned block representation");
        };
    }
    private FrontierV3FieldBlocks() { }
}
