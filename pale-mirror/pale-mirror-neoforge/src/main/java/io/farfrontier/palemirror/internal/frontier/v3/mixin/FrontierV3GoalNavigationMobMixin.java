package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3MobMotionLifecycle;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the owned NoAI body physically walkable without letting vanilla choose a task. */
@Mixin(Mob.class)
abstract class FrontierV3GoalNavigationMobMixin {
    @Inject(method = "isEffectiveAi", at = @At("HEAD"), cancellable = true, require = 1)
    private void frontierV3$allowOwnedPhysicalTravel(CallbackInfoReturnable<Boolean> callback) {
        if (FrontierV3MobMotionLifecycle.usesMinecraftGoalNavigation((Mob)(Object)this))
            callback.setReturnValue(true);
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true, require = 1)
    private void frontierV3$suppressVanillaTaskOwners(CallbackInfo callback) {
        if (FrontierV3MobMotionLifecycle.usesMinecraftGoalNavigation((Mob)(Object)this))
            callback.cancel();
    }
}
