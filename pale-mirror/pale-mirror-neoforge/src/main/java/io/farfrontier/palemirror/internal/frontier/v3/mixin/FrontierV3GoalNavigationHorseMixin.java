package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3MobMotionLifecycle;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Horse grazing runs outside serverAiStep and immobilizes the body even under NoAI.
 * Only the current goal-navigation owner suppresses that competing autonomous action. */
@Mixin(AbstractHorse.class)
abstract class FrontierV3GoalNavigationHorseMixin {
    @Inject(method = "aiStep", at = @At("HEAD"), require = 1)
    private void frontierV3$releaseGrazingBeforeOwnedTravel(CallbackInfo callback) {
        var horse = (AbstractHorse)(Object)this;
        if (FrontierV3MobMotionLifecycle.usesMinecraftGoalNavigation(horse) && horse.isEating())
            horse.setEating(false);
    }

    @Inject(method = "canEatGrass", at = @At("HEAD"), cancellable = true, require = 1)
    private void frontierV3$suspendAutonomousGrazing(CallbackInfoReturnable<Boolean> callback) {
        if (FrontierV3MobMotionLifecycle.usesMinecraftGoalNavigation((AbstractHorse)(Object)this))
            callback.setReturnValue(false);
    }
}
