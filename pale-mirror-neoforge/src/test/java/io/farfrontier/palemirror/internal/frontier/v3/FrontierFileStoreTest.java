package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.KernelPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierFileStoreTest {
    private static final WorldId WORLD = new WorldId("frontier:file-store");
    private static final SubjectId SUBJECT = new SubjectId("settlement:file-store");

    @Test
    void appendsRecoversSnapshotsAndCompactsOnlyCoveredWal(@TempDir Path directory) {
        FrontierFileStore store = new FrontierFileStore(directory, KernelPayloadCodecs.scheduleEffects());
        TransactionRecord transaction = transaction(1L);

        assertEquals(1L, store.append(transaction, Durability.DURABLE_BEFORE_EFFECT).walSequence());
        assertEquals(List.of(transaction), store.recover(WORLD).walTail());
        ScheduledAction action = ((ScheduleEffect.Created) transaction.events().getFirst().payload()).action();
        SnapshotRecord snapshot = new SnapshotRecord(new CheckpointImage(WORLD, new Revision(1L), new SimInstant(1L),
                new byte[] {3}, List.of(action), List.of()), 1L);
        store.installSnapshot(snapshot);
        assertEquals(0L, store.compact(WORLD, new Revision(1L)).retainedTransactionCount());
        assertEquals(snapshot, store.recover(WORLD).checkpoint().orElseThrow());
        assertTrue(store.recover(WORLD).walTail().isEmpty());
    }

    @Test
    void revisionGapsAndCorruptWalFailClosed(@TempDir Path directory) throws IOException {
        FrontierFileStore store = new FrontierFileStore(directory, KernelPayloadCodecs.scheduleEffects());
        assertThrows(IllegalStateException.class, () -> store.append(transaction(2L), Durability.BATCHABLE));
        Path temporary = directory.resolve("frontier-v3/frontier_file-store/wal-00000000000000000001.bin.tmp");
        Files.createDirectories(temporary.getParent());
        Files.write(temporary, new byte[] {9});
        store.append(transaction(1L), Durability.BATCHABLE);
        assertTrue(Files.notExists(temporary));
        Path wal = directory.resolve("frontier-v3/frontier_file-store/wal-00000000000000000001.bin");
        byte[] bytes = Files.readAllBytes(wal);
        bytes[bytes.length - 1] ^= 1;
        Files.write(wal, bytes);
        assertThrows(IllegalArgumentException.class, () -> store.recover(WORLD));
    }

    private static TransactionRecord transaction(long revision) {
        CommandId command = new CommandId("command:file-store-" + revision);
        TransactionId transaction = new TransactionId("transaction:file-store-" + revision);
        ScheduledAction action = new ScheduledAction(new ScheduleId("schedule:file-store-" + revision),
                new SimInstant(revision + 1L), 0, SUBJECT, "process.file-store", 1);
        FrontierEvent event = new FrontierEvent(1, new EventId("event:file-store-" + revision), transaction, WORLD,
                new Revision(revision), new SimInstant(revision), SUBJECT, CauseChain.root(command), new ScheduleEffect.Created(action));
        return new TransactionRecord(transaction, WORLD, new Revision(revision), new SimInstant(revision), List.of(event));
    }
}
