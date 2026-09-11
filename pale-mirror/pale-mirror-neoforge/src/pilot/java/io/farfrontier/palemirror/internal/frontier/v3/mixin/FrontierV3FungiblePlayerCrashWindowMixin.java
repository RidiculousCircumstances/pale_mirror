package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PilotCrashHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pilot-only observation at the real player split's physical/canonical gap. The production
 * adapter has already read the ordinary menu state; this probe neither writes inventory nor
 * changes the submitted handoff when it is unarmed.
 */
@Mixin(targets = "io.farfrontier.palemirror.internal.frontier.v3.FrontierV3FungibleResourceObservationExecutor")
abstract class FrontierV3FungiblePlayerCrashWindowMixin {
    @Inject(method = "observeOnePlayerDeparture", at = @At(value = "INVOKE",
            target = "Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3CommandSubmission;submit"
                    + "(Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3ServerRuntime;Ljava/lang/String;Ljava/lang/String;"
                    + "Lio/farfrontier/palemirror/frontier/v3/api/FrontierPayload;)Lio/farfrontier/palemirror/frontier/v3/api/CommandResult;"), require = 1)
    private static void stopAfterVisiblePlayerDeparture(ServerLevel level, @Coerce Object runtime, FrontierWorldState state,
                                                        CustodyAccount account, ChestBlockEntity chest, long epoch,
                                                        CallbackInfoReturnable<Boolean> ignored) {
        FrontierV3PilotCrashHooks.afterVisibleFungiblePlayerDeparture(level, runtime, account);
    }
}
