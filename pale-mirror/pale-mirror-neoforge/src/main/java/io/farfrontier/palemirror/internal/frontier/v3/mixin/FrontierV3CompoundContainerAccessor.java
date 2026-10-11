package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft's declared ordered composition, not inferred coordinates or matching item stacks. */
@Mixin(CompoundContainer.class)
public interface FrontierV3CompoundContainerAccessor {
    @Accessor("container1") Container frontierV3$first();
    @Accessor("container2") Container frontierV3$second();
}
