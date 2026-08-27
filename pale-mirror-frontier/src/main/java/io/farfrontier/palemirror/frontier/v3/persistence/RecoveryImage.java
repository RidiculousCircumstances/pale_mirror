package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandReceipt;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Complete bounded recovery input: newest verified snapshot plus ordered complete WAL tail. */
public record RecoveryImage(WorldId worldId, Optional<SnapshotRecord> checkpoint, List<TransactionRecord> walTail) {
    public RecoveryImage {
        Objects.requireNonNull(worldId, "world id");
        checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
        walTail = List.copyOf(walTail);
        checkpoint.ifPresent(record -> {
            if (!worldId.equals(record.checkpoint().worldId())) throw new IllegalArgumentException("checkpoint world does not match recovery image");
            validateReceipts(record);
        });
        walTail.forEach(transaction -> {
            if (!worldId.equals(transaction.worldId())) throw new IllegalArgumentException("WAL world does not match recovery image");
        });
        validateOrderedTail(checkpoint, walTail);
    }

    private static void validateReceipts(SnapshotRecord snapshot) {
        Set<CommandId> commandIds = new HashSet<>();
        for (CommandReceipt receipt : snapshot.checkpoint().receipts()) {
            if (!commandIds.add(receipt.commandId())) {
                throw new IllegalArgumentException("duplicate checkpoint command receipt: " + receipt.commandId().value());
            }
            if (receipt.revision().compareTo(snapshot.checkpoint().revision()) > 0
                    || receipt.submittedAt().compareTo(snapshot.checkpoint().instant()) > 0) {
                throw new IllegalArgumentException("checkpoint receipt exceeds checkpoint boundary");
            }
        }
    }

    /**
     * The filesystem host validates bytes and WAL sequence numbers; this pure boundary validates
     * the domain sequence independently, before a state codec or reducer can see the history.
     */
    private static void validateOrderedTail(Optional<SnapshotRecord> checkpoint, List<TransactionRecord> walTail) {
        Revision priorRevision = checkpoint.map(value -> value.checkpoint().revision()).orElse(Revision.ZERO);
        SimInstant priorInstant = checkpoint.map(value -> value.checkpoint().instant()).orElse(SimInstant.ZERO);
        Set<TransactionId> transactionIds = new HashSet<>();
        Set<EventId> eventIds = new HashSet<>();
        for (TransactionRecord transaction : walTail) {
            if (!transaction.revision().equals(priorRevision.next())) {
                throw new IllegalArgumentException("WAL revision is not contiguous at " + transaction.revision().value());
            }
            if (transaction.instant().compareTo(priorInstant) < 0) {
                throw new IllegalArgumentException("WAL simulation time moves backwards at " + transaction.revision().value());
            }
            if (!transactionIds.add(transaction.id())) {
                throw new IllegalArgumentException("duplicate WAL transaction id: " + transaction.id().value());
            }
            for (FrontierEvent event : transaction.events()) {
                if (!event.transactionId().equals(transaction.id())
                        || !event.worldId().equals(transaction.worldId())
                        || !event.revision().equals(transaction.revision())
                        || !event.instant().equals(transaction.instant())) {
                    throw new IllegalArgumentException("WAL event envelope does not match transaction " + transaction.id().value());
                }
                if (!eventIds.add(event.id())) {
                    throw new IllegalArgumentException("duplicate WAL event id: " + event.id().value());
                }
            }
            priorRevision = transaction.revision();
            priorInstant = transaction.instant();
        }
    }
}
