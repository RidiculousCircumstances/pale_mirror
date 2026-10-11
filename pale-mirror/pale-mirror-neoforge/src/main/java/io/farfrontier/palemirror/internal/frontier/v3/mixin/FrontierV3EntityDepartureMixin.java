package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3BodyObservationSave;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GroundItemDestruction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Final unload observation, after vanilla removes even an already-hidden entity. */
@Mixin(Entity.class)
abstract class FrontierV3EntityDepartureMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true, require = 1)
    private void frontierV3$beforeGroundRemoval(Entity.RemovalReason reason, CallbackInfo callback) {
        if (!FrontierV3GroundItemDestruction.before((Entity) (Object) this, reason)) callback.cancel();
    }
    @Inject(method = "load", at = @At("RETURN"), require = 1)
    private void frontierV3$observeLoadedBody(CompoundTag tag, CallbackInfo callback) {
        FrontierV3BodyObservationSave.observeLoad((Entity) (Object) this, tag);
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"), require = 1)
    private void frontierV3$observeSavedBody(CompoundTag tag, CallbackInfoReturnable<CompoundTag> callback) {
        FrontierV3BodyObservationSave.observe((Entity) (Object) this, callback.getReturnValue());
    }

    @Inject(method = "setRemoved", at = @At("TAIL"), require = 1)
    private void frontierV3$observeFinalChunkDeparture(Entity.RemovalReason reason, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        FrontierV3GroundItemDestruction.after(entity);
        if (entity.level() instanceof ServerLevel level) {
            FrontierV3ServerLifecycle.observeFinalChunkDeparture(level, entity);
        }
    }
}
