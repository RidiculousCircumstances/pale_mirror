package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
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
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecoveryImageTest {
    @Test
    void checkpointAndStoreReceiptsHaveTypedBoundaries() {
        WorldId world = new WorldId("frontier:recovery");
        CheckpointImage checkpoint = new CheckpointImage(world, Revision.ZERO, SimInstant.ZERO, new byte[] {1}, List.of(), List.of());

        RecoveryImage image = new RecoveryImage(world, Optional.of(new SnapshotRecord(checkpoint, 0L)), List.of());

        assertEquals(world, image.worldId());
        assertEquals(new Revision(2L), new AppendReceipt(
                new io.farfrontier.palemirror.frontier.v3.api.TransactionId("transaction:two"), new Revision(2L),
                Durability.DURABLE_BEFORE_EFFECT, 3L).revision());
        assertThrows(IllegalArgumentException.class, () -> new SnapshotReceipt(Revision.ZERO, -1L));
    }

    @Test
    void foreignCheckpointFailsRecoveryBeforeAnyReplay() {
        CheckpointImage checkpoint = new CheckpointImage(new WorldId("frontier:other"), Revision.ZERO, SimInstant.ZERO,
                new byte[] {1}, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new RecoveryImage(
                new WorldId("frontier:expected"), Optional.of(new SnapshotRecord(checkpoint, 0L)), List.of()));
    }

    @Test
    void duplicateOrFutureCheckpointReceiptsFailBeforeReplay() {
        WorldId world = new WorldId("frontier:receipts");
        CommandId command = new CommandId("command:receipt");
        io.farfrontier.palemirror.frontier.v3.api.CommandReceipt receipt = new io.farfrontier.palemirror.frontier.v3.api.CommandReceipt(
                command, SimInstant.ZERO, new TransactionId("transaction:receipt"), Revision.ZERO);
        CheckpointImage duplicate = new CheckpointImage(world, Revision.ZERO, SimInstant.ZERO, new byte[] {1}, List.of(), List.of(receipt, receipt));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryImage(world, Optional.of(new SnapshotRecord(duplicate, 0L)), List.of()));

        CheckpointImage future = new CheckpointImage(world, Revision.ZERO, SimInstant.ZERO, new byte[] {1}, List.of(), List.of(
                new io.farfrontier.palemirror.frontier.v3.api.CommandReceipt(command, new SimInstant(1L), receipt.transactionId(), Revision.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryImage(world, Optional.of(new SnapshotRecord(future, 0L)), List.of()));
    }

    @Test
    void tailMustBeContiguousMonotonicAndHaveMatchingEnvelopes() {
        WorldId world = new WorldId("frontier:ordered");
        TransactionRecord first = transaction(world, 1L, 3L, "one", "event:one");
        TransactionRecord second = transaction(world, 2L, 4L, "two", "event:two");
        assertEquals(List.of(first, second), new RecoveryImage(world, Optional.empty(), List.of(first, second)).walTail());

        assertThrows(IllegalArgumentException.class, () -> new RecoveryImage(world, Optional.empty(), List.of(second)));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryImage(world, Optional.empty(), List.of(
                first, transaction(world, 2L, 2L, "two", "event:two"))));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryImage(world, Optional.empty(), List.of(
                first, transaction(world, 2L, 4L, "one", "event:two"))));
    }

    private static TransactionRecord transaction(WorldId world, long revision, long instant, String id, String eventId) {
        TransactionId transactionId = new TransactionId("transaction:recovery-" + id);
        SubjectId subject = new SubjectId("subject:recovery");
        ScheduledAction action = new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:recovery-" + id),
                new SimInstant(instant + 1L), 0, subject, "process.recovery", 1);
        FrontierEvent event = new FrontierEvent(1, new EventId(eventId), transactionId, world,
                new Revision(revision), new SimInstant(instant), subject,
                CauseChain.root(new CommandId("command:recovery-" + id)), new ScheduleEffect.Created(action));
        return new TransactionRecord(transactionId, world, new Revision(revision), new SimInstant(instant), List.of(event));
    }
}
