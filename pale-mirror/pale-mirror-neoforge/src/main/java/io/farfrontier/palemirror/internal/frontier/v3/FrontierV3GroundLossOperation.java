package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.Function;

/** Owner-side confirmation for causal ground exits. The producer declares its protocol
 * and native recovery ports; this bridge never discovers a player or a cause by counts. */
final class FrontierV3GroundLossOperation {
    private FrontierV3GroundLossOperation() { }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            GroundStockExit receipt, FrontierV3OperationReceipt.Protocol protocol,
            Function<FrontierV3OperationReceipt, List<FrontierV3MinecraftOperation.Participant>> recoverPorts) {
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
            if (!operation.applied().equals(operation.declaration().participants())) {
                var ports = recoverPorts.apply(operation);
                if (ports.isEmpty()) return false;
                if (!ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet())
                        .equals(operation.declaration().participants())) {
                    FrontierV3OperationDiagnostics.result(level, operation, FrontierV3MinecraftOperation.Status.AMBIGUOUS); return false;
                }
                var observed = FrontierV3MinecraftOperation.observe(level, operation.declaration(), ports, () -> true).orElseThrow();
                if (!observed.complete()) return false;
                operation = ledger.require(operation.ticket());
            }
            journal.bindLossOperation(receipt, operation); journal.persist(level);
            if (!FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(), receipt)) return false;
        }
        journal.retireLoss(receipt); journal.persist(level); return true;
    }
}
