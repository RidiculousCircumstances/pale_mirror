package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3EntitySaveBoundary;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/** A pending chunk load means autoSave did not yet cover all retained entity locations. */
@Mixin(PersistentEntitySectionManager.class)
abstract class FrontierV3EntitySavePassMixin<T extends EntityAccess> {
    @Shadow @Final private EntityPersistentStorage<T> permanentStorage;
    @Unique private boolean frontierV3$completePass;

    @Inject(method = "autoSave", at = @At("HEAD"), require = 1)
    private void frontierV3$beginPass(CallbackInfo callback) { frontierV3$completePass = true; }

    @Inject(method = "storeChunkSections", at = @At("RETURN"), require = 1)
    private void frontierV3$observeSkippedChunk(long chunk, Consumer<T> consumer, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue()) frontierV3$completePass = false;
        else if (permanentStorage instanceof FrontierV3EntitySaveBoundary boundary) {
            // Vanilla has called storeEntities and then every final unload callback.
            // Publish all raw observations once for this chunk, not per entity.
            boundary.frontierV3$storedChunk();
        }
    }

    @Inject(method = "autoSave", at = @At("RETURN"), require = 1)
    private void frontierV3$endPass(CallbackInfo callback) {
        if (permanentStorage instanceof FrontierV3EntitySaveBoundary boundary) boundary.frontierV3$completeSavePass(frontierV3$completePass);
    }

    @Inject(method = "saveAll", at = @At("RETURN"), require = 1)
    private void frontierV3$endCompletePass(CallbackInfo callback) {
        // saveAll retries incomplete chunks before returning; an exception never reaches RETURN.
        if (permanentStorage instanceof FrontierV3EntitySaveBoundary boundary) boundary.frontierV3$completeSavePass(true);
    }
}
