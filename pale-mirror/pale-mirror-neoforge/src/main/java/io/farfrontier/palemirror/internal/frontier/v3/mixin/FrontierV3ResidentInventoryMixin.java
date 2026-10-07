package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResidentInventoryPersistence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.AbstractVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The same addressed pocket codec is used by every managed resident save and reload. */
@Mixin(AbstractVillager.class)
abstract class FrontierV3ResidentInventoryMixin {
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void paleMirror$saveIndexedPockets(CompoundTag tag, CallbackInfo ci) {
        FrontierV3ResidentInventoryPersistence.write((AbstractVillager) (Object) this, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void paleMirror$loadIndexedPockets(CompoundTag tag, CallbackInfo ci) {
        FrontierV3ResidentInventoryPersistence.read((AbstractVillager) (Object) this, tag);
    }
}
