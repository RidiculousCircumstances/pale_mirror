package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierStoreTransactionCommitterTest {
    private static final WorldId WORLD = new WorldId("frontier:committer");
    private static final TransactionRecord TRANSACTION = transaction();

    @Test
    void acceptsOnlyAReceiptForTheExactAppendedTransactionAndDurability() {
        FrontierStoreTransactionCommitter committer = new FrontierStoreTransactionCommitter(new StubStore(
                new AppendReceipt(TRANSACTION.id(), TRANSACTION.revision(), Durability.DURABLE_BEFORE_EFFECT, 1L)));

        assertDoesNotThrow(() -> committer.commit(TRANSACTION, Durability.DURABLE_BEFORE_EFFECT));
    }

    @Test
    void mismatchedStorageReceiptFailsClosed() {
        FrontierStoreTransactionCommitter committer = new FrontierStoreTransactionCommitter(new StubStore(
                new AppendReceipt(new TransactionId("transaction:wrong"), TRANSACTION.revision(), Durability.BATCHABLE, 1L)));

        assertThrows(IllegalStateException.class, () -> committer.commit(TRANSACTION, Durability.DURABLE_BEFORE_EFFECT));
    }

    private static TransactionRecord transaction() {
        CommandId command = new CommandId("command:committer");
        TransactionId transaction = new TransactionId("transaction:committer");
        FrontierEvent event = new FrontierEvent(1, new EventId("event:committer"), transaction, WORLD,
                new Revision(1L), new SimInstant(1L), new SubjectId("settlement:committer"), CauseChain.root(command),
                new ScheduleEffect.Cancelled(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:committer")));
        return new TransactionRecord(transaction, WORLD, new Revision(1L), new SimInstant(1L), List.of(event));
    }

    private record StubStore(AppendReceipt receipt) implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { throw new UnsupportedOperationException(); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) { return receipt; }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException(); }
        @Override public CompactionReceipt compact(WorldId worldId, Revision coveredRevision) { throw new UnsupportedOperationException(); }
    }
}
