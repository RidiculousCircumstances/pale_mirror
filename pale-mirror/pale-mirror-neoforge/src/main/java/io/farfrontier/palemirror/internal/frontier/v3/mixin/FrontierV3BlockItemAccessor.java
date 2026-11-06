package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read the actual native placement proposal, including subclass validation. */
@Mixin(BlockItem.class)
public interface FrontierV3BlockItemAccessor {
    @Invoker("getPlacementState") BlockState frontierV3$placementState(BlockPlaceContext context);
}
