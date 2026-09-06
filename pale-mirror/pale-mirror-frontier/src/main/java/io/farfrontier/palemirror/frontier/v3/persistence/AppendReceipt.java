package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;

import java.util.Objects;

/** Evidence that one complete transaction reached the required durability boundary. */
public record AppendReceipt(TransactionId transactionId, Revision revision, Durability durability, long walSequence) {
    public AppendReceipt {
        Objects.requireNonNull(transactionId, "transaction id");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(durability, "durability");
        if (walSequence < 0L) throw new IllegalArgumentException("WAL sequence cannot be negative");
    }
}
