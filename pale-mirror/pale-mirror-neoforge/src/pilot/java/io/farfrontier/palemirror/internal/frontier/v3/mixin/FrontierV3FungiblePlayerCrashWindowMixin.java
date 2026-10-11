package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PilotCrashHooks;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Pilot-only observation at the retained world-carrier/player handoff gap. Managed
 * container clicks have their own stock-exit journal, not this historical player-account
 * handoff. The production adapter has already read the physical state; this probe neither writes inventory nor
 * changes the submitted handoff when it is unarmed.
 */
@Mixin(targets = "io.farfrontier.palemirror.internal.frontier.v3.FrontierV3FungibleResourceObservationExecutor")
abstract class FrontierV3FungiblePlayerCrashWindowMixin {
    @Inject(method = "observeOneWorldPickup", at = @At(value = "INVOKE",
            target = "Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3CommandSubmission;submit"
                    + "(Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3ServerRuntime;Ljava/lang/String;Ljava/lang/String;"
                    + "Lio/farfrontier/palemirror/frontier/v3/api/FrontierPayload;)Lio/farfrontier/palemirror/frontier/v3/api/CommandResult;"), require = 1)
    private static void stopAfterVisiblePlayerDeparture(ServerLevel level, @Coerce Object runtime, FrontierWorldState state,
                                                        CallbackInfoReturnable<Boolean> ignored,
                                                        @Local CustodyAccount account) {
        FrontierV3PilotCrashHooks.afterVisibleFungiblePlayerDeparture(level, runtime, account);
    }
}
