package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Exact engine representation of owned cultivable soils; grass is not silently rewritten as dirt. */
final class FrontierV3FieldBlocks {
    static BlockState crop(io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind kind, int age) {
        var id = net.minecraft.resources.ResourceLocation.parse(kind.crop().blockKind());
        var registry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        if (!registry.containsKey(id) || !(registry.get(id) instanceof net.minecraft.world.level.block.CropBlock crop)
                || crop.getMaxAge() != io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle.MATURE_STAGE)
            throw new IllegalArgumentException("configured crop is unavailable or has an unsupported growth scale: " + id);
        return crop.getStateForAge(age);
    }
    static boolean isCrop(BlockState state, io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind kind) {
        return state.getBlock() == crop(kind, 0).getBlock();
    }
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
