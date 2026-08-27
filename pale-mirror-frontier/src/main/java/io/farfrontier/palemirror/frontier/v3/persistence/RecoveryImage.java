package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Complete bounded recovery input: newest verified snapshot plus ordered complete WAL tail. */
public record RecoveryImage(WorldId worldId, Optional<SnapshotRecord> checkpoint, List<TransactionRecord> walTail) {
    public RecoveryImage {
        Objects.requireNonNull(worldId, "world id");
        checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
        walTail = List.copyOf(walTail);
        checkpoint.ifPresent(record -> {
            if (!worldId.equals(record.checkpoint().worldId())) throw new IllegalArgumentException("checkpoint world does not match recovery image");
        });
        walTail.forEach(transaction -> {
            if (!worldId.equals(transaction.worldId())) throw new IllegalArgumentException("WAL world does not match recovery image");
        });
    }
}
