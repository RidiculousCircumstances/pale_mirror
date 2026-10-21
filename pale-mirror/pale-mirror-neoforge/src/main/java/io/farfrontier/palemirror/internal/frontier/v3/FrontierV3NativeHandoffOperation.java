package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Retains a resource owner's witness across common-operation retirement. The source
 * adapter supplies the causal native callback; this bridge has no inventory policy. */
final class FrontierV3NativeHandoffOperation {
    private FrontierV3NativeHandoffOperation() { }
    static void apply(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, UUID id,
            FungibleResourceHandoffObserved receipt, FrontierV3OperationReceipt.Protocol protocol,
            List<FrontierV3MinecraftOperation.Participant> ports, BooleanSupplier before, BooleanSupplier mutation, BooleanSupplier after) {
        var declaration = new FrontierV3OperationReceipt.Declaration(protocol, FrontierExecutionSubjects.PHYSICAL_EXECUTOR,
                UUID.randomUUID().toString(), FrontierV3OperationConfirmation.fingerprint(receipt),
                ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
        var operations = FrontierV3OperationLedger.get(level);
        if (!operations.canPrepare(declaration)) {
            FrontierV3OperationDiagnostics.waiting(level, declaration, FrontierV3OperationDiagnostics.Backpressure.JOURNAL_CAPACITY); return;
        }
        var operation = operations.prepare(declaration); var journal = FrontierV3ResourceHandoffLedger.get(level);
        journal.prepareNative(id, receipt, operation); journal.persist(level);
        operations.persist(level); // Admission/pruning must see the acknowledged common prepare, not merely the owner WAL.
        var result = FrontierV3MinecraftOperation.apply(level, declaration, ports, before, mutation, after);
        if (result.status() == FrontierV3MinecraftOperation.Status.WAITING_DURABILITY
                || result.status() == FrontierV3MinecraftOperation.Status.NOT_APPLIED) {
            // Only an explicitly unattempted action can be cancelled. Never retire an
            // ambiguous effect merely because its native callback returned no result.
            if (operations.find(operation.ticket()).isPresent() && !operations.cancelUnattempted(operation.ticket()))
                throw new IllegalStateException("unapplied handoff already consumed its native attempt");
            operations.persist(level); journal.retire(id, receipt); journal.persist(level); return;
        }
        if (result.complete()) reconcile(level, runtime, id, receipt);
    }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            UUID id, FungibleResourceHandoffObserved receipt) {
        var journal = FrontierV3ResourceHandoffLedger.get(level); var linked = journal.handoffOperation(id).orElseThrow();
        var operations = FrontierV3OperationLedger.get(level); var current = operations.find(linked.ticket()).orElse(null);
        if (current == null) {
            if (!operations.prunable(linked.nativeReceipt(linked.declaration().participants().iterator().next()))) return false;
            // The positive retirement fence plus this durable owner phase closes both
            // cuts: PREPARED means an explicitly cancelled, never-attempted callback;
            // APPLIED means confirmed completion. We force APPLIED below before any
            // canonical submit, so resolved completion cannot retain PREPARED here.
            // Neither case permits another physical effect or a new canonical command.
        } else {
            if (!current.declaration().equals(linked.declaration())) throw new IllegalStateException("native handoff changed declaration");
            if (!FrontierV3MinecraftOperation.recoverOutcome(level, current.ticket())) return false;
            current = operations.require(current.ticket());
            journal.bindHandoffOperation(id, current); journal.persist(level);
            if (!FrontierV3OperationConfirmation.submit(level, runtime, current.ticket(), receipt)) return false;
        }
        journal.retire(id, receipt); journal.persist(level); return true;
    }
}
