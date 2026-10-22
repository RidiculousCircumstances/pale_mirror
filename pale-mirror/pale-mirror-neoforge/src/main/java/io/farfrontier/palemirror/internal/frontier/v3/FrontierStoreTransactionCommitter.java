package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;

import java.util.Objects;
/** Server-owned adapter which turns the pure engine's write-ahead boundary into a v3 WAL append. */
final class FrontierStoreTransactionCommitter implements TransactionCommitter {
    private final FrontierStore store;

    FrontierStoreTransactionCommitter(FrontierStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public void commit(TransactionRecord transaction, Durability durability) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(durability, "durability");
        AppendReceipt receipt = store.append(transaction, durability);
        validate(transaction, durability, receipt);
    }

    @Override public Confirmation prepare(TransactionRecord transaction, Durability durability) {
        var submitted = store.prepareAppend(transaction, durability);
        return () -> {
            var receipt = submitted.poll();
            receipt.ifPresent(value -> validate(transaction, durability, value));
            return receipt.isPresent();
        };
    }

    private static void validate(TransactionRecord transaction, Durability durability, AppendReceipt receipt) {
        if (!transaction.id().equals(receipt.transactionId()) || !transaction.revision().equals(receipt.revision())
                || durability != receipt.durability()) {
            throw new IllegalStateException("Frontier v3 store returned a mismatched append receipt");
        }
    }
}
