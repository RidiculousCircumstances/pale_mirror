package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native collision declaration, separate from the position-dependent visual outline. */
@Mixin(BlockBehaviour.class)
public interface FrontierV3BlockCollisionAccessor {
    @Accessor("hasCollision") boolean paleMirror$hasCollision();
}
