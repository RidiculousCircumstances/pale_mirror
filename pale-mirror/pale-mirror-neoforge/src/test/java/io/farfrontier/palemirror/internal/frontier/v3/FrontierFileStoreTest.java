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
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyReleased;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaEmitted;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaObserved;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaRecord;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
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
    void fileStoreRecoversReplicaCustodySnapshotAndFencedWalTransition(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:replica-file-store");
        SubjectId object = new SubjectId("container:file-store-a"), scope = new SubjectId("scope:file-store-a"), provider = new SubjectId("provider:file-store-a");
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty()
                .declare(PhysicalReplicaRecord.expected(object, "container.depot", 10L, "sha256:a", "owned:genesis"))
                .observe(object, 10L, 1L, "sha256:a", "owned:genesis", 10L)
                .acquire(new PhysicalCustodyLease(scope, object, provider, 1L, 10L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        FrontierWorldState snapshotState = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L))
                .withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        TransactionId transactionId = new TransactionId("transaction:replica-file-store");
        CustodyReleased released = new CustodyReleased(scope, 1L, 10L, 2L);
        TransactionRecord transaction = new TransactionRecord(transactionId, world, new Revision(1L), new SimInstant(1L), List.of(
                new FrontierEvent(1, new EventId("event:replica-file-store"), transactionId, world, new Revision(1L), new SimInstant(1L),
                        scope, CauseChain.root(new CommandId("command:replica-file-store")), released)));
        FrontierFileStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        store.installSnapshot(new SnapshotRecord(new CheckpointImage(world, Revision.ZERO, SimInstant.ZERO,
                new FrontierWorldStateCodec().encode(snapshotState), List.of(), List.of()), 0L));
        store.append(transaction, Durability.DURABLE_BEFORE_EFFECT);

        var recovered = store.recover(world);
        FrontierWorldState hydrated = new FrontierWorldStateCodec().decode(recovered.checkpoint().orElseThrow().checkpoint().canonicalState());
        assertEquals(transaction, recovered.walTail().getFirst());
        FrontierWorldState replayed = FrontierWorldProcessCatalog.reduce("replica-custody", hydrated, recovered.walTail().getFirst().events().getFirst());
        assertEquals(PhysicalCustodyLeaseStatus.RELEASED, replayed.replicaCustody().custodyByScope().get(scope).status());
        assertEquals(2L, replayed.replicaCustody().custodyByScope().get(scope).expectedReplicaRevision());
    }

    @Test
    void fileStoreReplaysASecondEmissionAndObservationCycleForOneStableObject(@TempDir Path directory) {
        WorldId world = new WorldId("frontier:replica-file-store-cycle");
        SubjectId object = new SubjectId("container:file-store-cycle"), scope = new SubjectId("scope:file-store-cycle"), provider = new SubjectId("provider:file-store-cycle");
        PhysicalReplicaCustodyState firstCycle = PhysicalReplicaCustodyState.empty()
                .declare(PhysicalReplicaRecord.expected(object, "container.depot", 10L, "sha256:a", "owned:genesis"))
                .observe(object, 10L, 1L, "sha256:a", "owned:genesis", 10L)
                .acquire(new PhysicalCustodyLease(scope, object, provider, 1L, 10L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null))
                .release(scope, 1L, 10L, 2L);
        FrontierWorldState snapshotState = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L))
                .withChanges(FrontierWorldStateUpdate.begin().replicaCustody(firstCycle));
        FrontierFileStore store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        store.installSnapshot(new SnapshotRecord(new CheckpointImage(world, Revision.ZERO, SimInstant.ZERO,
                new FrontierWorldStateCodec().encode(snapshotState), List.of(), List.of()), 0L));
        store.append(replicaTransaction(world, 1L, object, new ReplicaEmitted(object, 10L, 2L, 11L, "sha256:b", "owned:cycle-two")), Durability.BATCHABLE);
        store.append(replicaTransaction(world, 2L, object, new ReplicaObserved(object, 11L, 3L, "sha256:b", "owned:cycle-two", 11L)), Durability.BATCHABLE);

        var recovered = store.recover(world);
        FrontierWorldState replayed = new FrontierWorldStateCodec().decode(recovered.checkpoint().orElseThrow().checkpoint().canonicalState());
        for (TransactionRecord transaction : recovered.walTail()) replayed = FrontierWorldProcessCatalog.reduce("replica-custody", replayed, transaction.events().getFirst());
        assertEquals(11L, replayed.replicaCustody().replicas().get(object).emittedCanonicalRevision());
        assertEquals(PhysicalCustodyLeaseStatus.RELEASED, replayed.replicaCustody().custodyByScope().get(scope).status());
        assertEquals(4L, replayed.replicaCustody().replicas().get(object).replicaRevision());
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

    @Test
    void replacesAnIdleSnapshotAtTheSameCoveredWalSequenceAtomically(@TempDir Path directory) {
        FrontierFileStore store = new FrontierFileStore(directory, KernelPayloadCodecs.scheduleEffects());
        SnapshotRecord initial = new SnapshotRecord(new CheckpointImage(WORLD, Revision.ZERO, SimInstant.ZERO,
                new byte[] {1}, List.of(), List.of()), 0L);
        SnapshotRecord advancedTime = new SnapshotRecord(new CheckpointImage(WORLD, Revision.ZERO, new SimInstant(20L),
                new byte[] {1}, List.of(), List.of()), 0L);

        store.installSnapshot(initial);
        store.installSnapshot(advancedTime);

        assertEquals(advancedTime, store.recover(WORLD).checkpoint().orElseThrow());
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
    private static TransactionRecord replicaTransaction(WorldId world, long revision, SubjectId subject, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        TransactionId transaction = new TransactionId("transaction:replica-cycle-" + revision); CommandId command = new CommandId("command:replica-cycle-" + revision);
        FrontierEvent event = new FrontierEvent(1, new EventId("event:replica-cycle-" + revision), transaction, world, new Revision(revision),
                new SimInstant(revision), subject, CauseChain.root(command), payload);
        return new TransactionRecord(transaction, world, new Revision(revision), new SimInstant(revision), List.of(event));
    }
}
