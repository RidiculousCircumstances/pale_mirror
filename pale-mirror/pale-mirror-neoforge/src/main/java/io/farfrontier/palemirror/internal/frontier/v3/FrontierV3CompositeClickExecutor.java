package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;

/** Confirms the outcome captured inside the actual native callback. A later layout
 * comparison cannot invent either the callback or its canonical acceptance. */
final class FrontierV3CompositeClickExecutor {
    private FrontierV3CompositeClickExecutor() { }
    static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, FrontierV3ContainerClickTransaction witness) {
        var transaction = FrontierV3PlayerClickOperation.ready(level, state, witness);
        if (transaction == null) return false;
        var ledger = FrontierV3DepotClickLedger.get(level);
        if (FrontierV3PlayerClickOperation.confirmed(level, transaction)) {
            ledger.retire(transaction); ledger.persist(level); return true;
        }
        var outcome = transaction.outcome().orElse(null);
        if (outcome == null) {
            FrontierV3ContainerClickExecutor.conflict(runtime, transaction.members().getFirst(), "native click has no captured outcome");
            return false;
        }
        boolean unchanged = outcome.exactIngress().isEmpty()
                && outcome.members().stream().allMatch(member -> member.before().equals(member.after()))
                && outcome.returning().map(source -> source.quantity() == source.remaining()).orElse(true);
        if (unchanged) {
            ledger.retireNoEffect(transaction, FrontierV3PlayerClickOperation.operation(level, transaction).orElseThrow(), level);
            return true;
        }
        var confirmation = FrontierV3PlayerClickOperation.confirm(level, runtime, transaction, outcome);
        if (confirmation != FrontierV3OperationConfirmation.Outcome.CONFIRMED) {
            if (confirmation == FrontierV3OperationConfirmation.Outcome.REJECTED)
                FrontierV3ContainerClickExecutor.conflict(runtime, transaction.members().getFirst(), "captured canonical click outcome rejected");
            return false;
        }
        ledger.retire(transaction); ledger.persist(level); return true;
    }
}
