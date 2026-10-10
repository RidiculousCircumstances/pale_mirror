package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.inventory.HorseInventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact menu target, never a nearby-animal lookup. */
@Mixin(HorseInventoryMenu.class)
public interface FrontierV3HorseMenuAccessor {
    @Accessor("horse") AbstractHorse frontierV3$horse();
}
