package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3AutomationTransfers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
abstract class FrontierV3AutomationEjectionMixin {
    @Inject(method = "ejectItems", at = @At("HEAD"))
    private static void frontierV3$begin(Level level, BlockPos pos, HopperBlockEntity hopper, CallbackInfoReturnable<Boolean> callback) {
        FrontierV3AutomationTransfers.beginEjection();
    }
    @Inject(method = "ejectItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getAttachedContainer"
            + "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)Lnet/minecraft/world/Container;"), cancellable = true)
    private static void frontierV3$noSecondAttempt(Level level, BlockPos pos, HopperBlockEntity hopper, CallbackInfoReturnable<Boolean> callback) {
        if (FrontierV3AutomationTransfers.deniedEjection(hopper)) {
            FrontierV3AutomationTransfers.endEjection(); callback.setReturnValue(false);
        }
    }
    @Inject(method = "ejectItems", at = @At("RETURN"))
    private static void frontierV3$end(Level level, BlockPos pos, HopperBlockEntity hopper, CallbackInfoReturnable<Boolean> callback) {
        FrontierV3AutomationTransfers.endEjection();
    }
}
