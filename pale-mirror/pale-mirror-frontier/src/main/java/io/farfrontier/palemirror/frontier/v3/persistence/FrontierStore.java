package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;

/** Storage port. NeoForge owns the implementation; the v3 kernel owns record validation and replay. */
public interface FrontierStore {
    RecoveryImage recover(WorldId worldId);

    AppendReceipt append(TransactionRecord transaction, Durability durability);

    SnapshotReceipt installSnapshot(SnapshotRecord snapshot);

    CompactionReceipt compact(WorldId worldId, Revision coveredRevision);
}
