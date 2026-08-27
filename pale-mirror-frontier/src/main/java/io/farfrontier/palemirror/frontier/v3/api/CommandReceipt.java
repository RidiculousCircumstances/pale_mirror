package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Bounded durable idempotency record for one accepted command. */
public record CommandReceipt(CommandId commandId, SimInstant submittedAt, TransactionId transactionId, Revision revision) {
    public CommandReceipt {
        Objects.requireNonNull(commandId, "command id");
        Objects.requireNonNull(submittedAt, "submitted at");
        Objects.requireNonNull(transactionId, "transaction id");
        Objects.requireNonNull(revision, "revision");
    }
}
