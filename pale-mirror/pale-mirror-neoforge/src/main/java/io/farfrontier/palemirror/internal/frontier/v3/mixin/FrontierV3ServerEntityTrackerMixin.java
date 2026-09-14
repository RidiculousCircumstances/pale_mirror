package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3MobMotionLifecycle;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only observation of vanilla tracking for an already controlled body. */
@Mixin(ServerEntity.class)
abstract class FrontierV3ServerEntityTrackerMixin {
    @Shadow private Entity entity;

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void frontierV3$observeVanillaTracker(CallbackInfo callback) {
        if (entity instanceof Mob mob) FrontierV3MobMotionLifecycle.observeVanillaTracker(mob);
    }
}
