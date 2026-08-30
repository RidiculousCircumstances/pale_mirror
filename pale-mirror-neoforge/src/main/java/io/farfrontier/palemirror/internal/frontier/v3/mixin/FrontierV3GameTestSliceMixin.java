package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GameTestSlice;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Narrows only an explicitly requested dedicated GameTest-server run. */
@Mixin(GameTestRegistry.class)
abstract class FrontierV3GameTestSliceMixin {
    /**
     * GameTestServer obtains the functions inside a compiler-generated Main lambda, so filtering
     * that call site is brittle. Filter this read-only registry view instead; no property means
     * the original collection is returned byte-for-byte and normal/full launches are unchanged.
     */
    @Inject(method = "getAllTestFunctions", at = @At("RETURN"), cancellable = true)
    private static void frontierV3$selectGameTestSlice(CallbackInfoReturnable<Collection<TestFunction>> callback) {
        String slice = System.getProperty(FrontierV3GameTestSlice.PROPERTY, "");
        if (slice.isBlank()) return;
        callback.setReturnValue(callback.getReturnValue().stream()
                .filter(test -> FrontierV3GameTestSlice.includes(slice, test.batchName()))
                .toList());
    }
}
