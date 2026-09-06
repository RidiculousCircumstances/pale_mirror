package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PilotCrashHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pilot-only rendezvous after the production committer has validated an exact WAL append.
 *
 * <p>This is deliberately a tail injection: the receipt has already been checked, but control
 * has not returned to the engine to install the canonical transition.  The distributable
 * committer has no callback, property or probe dependency.</p>
 */
@Mixin(targets = "io.farfrontier.palemirror.internal.frontier.v3.FrontierStoreTransactionCommitter")
abstract class FrontierV3DurableCrashWindowMixin {
    @Inject(method = "commit", at = @At("TAIL"), require = 1)
    private void stopAfterDurableAppend(TransactionRecord transaction, Durability durability, CallbackInfo ignored) {
        FrontierV3PilotCrashHooks.afterDurableAppend(transaction);
    }
}
