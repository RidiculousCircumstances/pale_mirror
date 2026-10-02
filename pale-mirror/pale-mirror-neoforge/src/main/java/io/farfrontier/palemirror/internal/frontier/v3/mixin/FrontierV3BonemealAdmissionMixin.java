package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3FieldInteractionAdmission;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Admission precedes vanilla's block-state read, not its already-computed replacement write. */
@Mixin(BoneMealItem.class)
abstract class FrontierV3BonemealAdmissionMixin {
    @Inject(method = "applyBonemeal", at = @At("HEAD"), cancellable = true, require = 1)
    private static void frontierV3$prepareFieldCell(ItemStack stack, Level world, BlockPos position,
                                                   Player player, CallbackInfoReturnable<Boolean> callback) {
        if (world instanceof ServerLevel level && !FrontierV3FieldInteractionAdmission.prepareBonemeal(level, position))
            callback.setReturnValue(false);
    }
}
