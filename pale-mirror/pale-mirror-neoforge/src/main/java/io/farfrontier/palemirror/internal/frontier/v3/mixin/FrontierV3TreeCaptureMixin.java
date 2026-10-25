package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3TreeCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

/** Pinned 1.21.1 TreeFeature retains roots/logs/leaves/decorations in separate local sets. */
@Mixin(TreeFeature.class)
abstract class FrontierV3TreeCaptureMixin {
    @Inject(method = "place", at = @At("RETURN"), require = 1)
    private void frontierV3$captureSuccessfulTree(FeaturePlaceContext<TreeConfiguration> context,
            CallbackInfoReturnable<Boolean> callback, @Local(ordinal = 1) Set<BlockPos> logs) {
        if (callback.getReturnValueZ()) FrontierV3TreeCapture.generated(context.level(), logs);
    }
}
