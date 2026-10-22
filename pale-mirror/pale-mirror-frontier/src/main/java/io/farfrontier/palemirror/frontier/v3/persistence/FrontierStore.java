package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;

/** Storage port. NeoForge owns the implementation; the v3 kernel owns record validation and replay. */
public interface FrontierStore {
    RecoveryImage recover(WorldId worldId);

    AppendReceipt append(TransactionRecord transaction, Durability durability);

    /** A submitted append is not a receipt. Only a present result confirms the requested boundary. */
    @FunctionalInterface interface Submission { java.util.Optional<AppendReceipt> poll(); }
    default Submission prepareAppend(TransactionRecord transaction, Durability durability) {
        var receipt = append(transaction, durability);
        return () -> java.util.Optional.of(receipt);
    }

    SnapshotReceipt installSnapshot(SnapshotRecord snapshot);

    CompactionReceipt compact(WorldId worldId, Revision coveredRevision);
}
