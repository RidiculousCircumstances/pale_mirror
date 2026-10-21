package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SidedInvWrapper.class, remap = false)
public interface FrontierV3SidedInventoryAccessor {
    @Accessor("inv") WorldlyContainer frontierV3$inventory();
    @Accessor("side") Direction frontierV3$side();
}
