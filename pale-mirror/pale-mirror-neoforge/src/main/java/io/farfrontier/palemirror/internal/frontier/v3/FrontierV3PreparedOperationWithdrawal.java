package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import net.minecraft.server.level.ServerLevel;

/** Cancels an exact canonical intent only before native attempt. No owner-specific policy.
 * Journal entries cannot retire before canonical closure, so an absent exact entry
 * proves an outstanding canonical intent never reached native preparation. The
 * canonical durability barrier excludes all native execution until cancellation commits.
 */
final class FrontierV3PreparedOperationWithdrawal {
    private FrontierV3PreparedOperationWithdrawal() { }
    static boolean submit(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime,
            FrontierV3OperationReceipt.Protocol protocol, SubjectId owner, FrontierPayload prepared, FrontierPayload cancelled) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("withdrawal outside server thread");
        if (runtime.awaitingCanonicalDurability()) return false;
        var ledger = FrontierV3OperationLedger.get(level);
        var operation = ledger.find(protocol, owner, FrontierV3OperationConfirmation.fingerprint(prepared));
        if (operation.isEmpty()) return FrontierV3CommandSubmission.submit(runtime,
                "unprepared-withdrawal", owner.value(), cancelled) instanceof CommandResult.Accepted;
        var receipt = operation.orElseThrow();
        if (!ledger.sealUnattemptedCancellation(receipt.ticket())) return false;
        ledger.persist(level);
        return FrontierV3OperationConfirmation.submitRecovery(level, runtime, receipt.ticket(), cancelled, java.util.Optional.empty());
    }
}
