package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Exact engine representation of owned cultivable soils; grass is not silently rewritten as dirt. */
final class FrontierV3FieldBlocks {
    static ResourceFieldCycle.Soil soil(BlockState block) {
        return ResourceFieldCycle.Soil.fromBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(block.getBlock()).toString());
    }
    static BlockState soil(ResourceFieldCycle.Soil value) {
        var id = net.minecraft.resources.ResourceLocation.parse(value.blockKind());
        var registry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        if (!registry.containsKey(id)) throw new IllegalArgumentException("configured soil block is unavailable: " + id);
        return registry.get(id).defaultBlockState();
    }
    private FrontierV3FieldBlocks() { }
}
