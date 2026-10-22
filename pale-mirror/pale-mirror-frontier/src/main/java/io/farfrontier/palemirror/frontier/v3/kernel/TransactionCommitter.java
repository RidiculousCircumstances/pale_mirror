package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.persistence.Durability;

import java.util.Objects;

/**
 * Pure write-ahead boundary for a completed canonical transaction.
 *
 * <p>The engine invokes this before it installs the transaction's state, revision, schedule or
 * command receipt. NeoForge supplies the filesystem-backed implementation; a failed commit is
 * therefore visible as an engine quarantine instead of an acknowledged, unrecorded mutation.</p>
 */
@FunctionalInterface
public interface TransactionCommitter {
    void commit(TransactionRecord transaction, Durability durability);

    /** One exact, bounded in-flight transaction. False grants no publication/effect authority.
     * The host polls on the kernel's owner thread; writer failure must propagate, not become ready. */
    @FunctionalInterface interface Confirmation { boolean ready(); }

    default Confirmation prepare(TransactionRecord transaction, Durability durability) {
        commit(transaction, durability);
        return () -> true;
    }

    static TransactionCommitter noOp() {
        return (transaction, durability) -> {
            Objects.requireNonNull(transaction, "transaction");
            Objects.requireNonNull(durability, "durability");
        };
    }
}
