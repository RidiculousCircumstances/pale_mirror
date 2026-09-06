package io.farfrontier.palemirror.visuals.integration.villageroverhaul.mixin;

import io.farfrontier.palemirror.visuals.resident.ManagedResident;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact-version isolation: PM citizens trade normally but cannot become player-owned recruits. */
@Mixin(targets = "org.z2six.villageroverhaul.server.RecruitService", remap = false)
abstract class RecruitServiceMixin {
    @Inject(method = "isEligible", at = @At("HEAD"), cancellable = true, remap = false)
    private static void paleMirror$denyManagedResidentRecruitment(Entity entity,
                                                                  CallbackInfoReturnable<Boolean> callback) {
        if (ManagedResident.isManaged(entity)) callback.setReturnValue(false);
    }
}
