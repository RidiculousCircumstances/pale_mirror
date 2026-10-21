package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Owner-side confirmation for causal ground exits. The producer declares its protocol
 * and exact native participants; this bridge never discovers a player or a cause by counts. */
final class FrontierV3GroundLossOperation {
    private FrontierV3GroundLossOperation() { }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            GroundStockExit receipt, FrontierV3OperationReceipt.Protocol protocol) {
        var journal = FrontierV3ResourceHandoffLedger.get(level); var ledger = FrontierV3OperationLedger.get(level);
        var linked = journal.lossOperation(receipt).orElse(null);
        var operation = linked == null ? ledger.find(protocol, receipt.economicOwner(), receipt.witnessId().toString()).orElse(null)
                : ledger.find(linked.ticket()).orElse(null);
        if (operation == null) {
            if (linked == null || linked.declaration().protocol() != protocol
                    || !ledger.prunable(linked.nativeReceipt(linked.declaration().participants().iterator().next()))) return false;
        } else {
            if (operation.declaration().protocol() != protocol
                    || !operation.declaration().fingerprint().equals(FrontierV3OperationConfirmation.fingerprint(receipt)))
                throw new IllegalStateException("ground exit changed its declared native intent");
            if (!FrontierV3MinecraftOperation.recoverOutcome(level, operation.ticket())) return false;
            operation = ledger.require(operation.ticket());
            journal.bindLossOperation(receipt, operation); journal.persist(level);
            if (!FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(), receipt)) return false;
        }
        journal.retireLoss(receipt); journal.persist(level); return true;
    }
}
