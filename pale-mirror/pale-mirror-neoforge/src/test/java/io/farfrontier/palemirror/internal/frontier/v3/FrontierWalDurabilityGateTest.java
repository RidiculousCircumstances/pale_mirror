package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierWalDurabilityGateTest {
    @Test void ordinaryCriticalSubmissionYieldsUntilItsExactPrefixIsForced(@TempDir Path dir) throws Exception {
        try (var lane = new StepWriter(); var store = new FrontierFileStore(dir, KernelPayloadCodecs.scheduleEffects(), lane)) {
            assertTrue(store.tryContinueTurn());
            store.prepareAppend(transaction(1), Durability.BATCHABLE).poll().orElseThrow();
            var critical = store.prepareAppend(transaction(2), Durability.DURABLE_BEFORE_EFFECT);
            assertTrue(critical.poll().isEmpty()); assertEquals(1, lane.pending.size());
            assertTrue(critical.poll().isEmpty()); assertEquals(0, store.flushedSequence(WORLD));
            store.yieldTurnAsync(); assertTrue(store.tryContinueTurn()); store.yieldTurnAsync();
            lane.release(); assertTrue(store.tryContinueTurn());
            assertEquals(2, critical.poll().orElseThrow().walSequence());
            assertEquals(2, store.flushedSequence(WORLD));
            assertEquals(2, critical.poll().orElseThrow().walSequence(), "polling cannot append twice");
            store.yieldTurnAsync();
        }
        try (var recovered = new FrontierFileStore(dir, KernelPayloadCodecs.scheduleEffects())) {
            assertEquals(List.of(transaction(1), transaction(2)), recovered.recover(WORLD).walTail());
        }
    }
    private static final WorldId WORLD = new WorldId("frontier:wal-gate");
    private static TransactionRecord transaction(long revision) {
        var id = new TransactionId("transaction:gate-" + revision);
        var instant = new SimInstant(revision); var actor = new SubjectId("subject:gate");
        var action = new ScheduledAction(new ScheduleId("schedule:gate-" + revision), instant, 1, actor, "test.gate", 1);
        return new TransactionRecord(id, WORLD, new Revision(revision), instant, List.of(new FrontierEvent(1,
                new EventId("event:gate-" + revision), id, WORLD, new Revision(revision), instant, actor,
                CauseChain.root(new CommandId("command:gate")), new ScheduleEffect.Created(action))));
    }
    private static final class StepWriter implements Executor, AutoCloseable {
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        boolean delayed = true;
        @Override public void execute(Runnable task) { if (delayed) pending.add(task); else worker.execute(task); }
        void release() throws Exception {
            delayed = false; while (!pending.isEmpty()) worker.execute(pending.remove());
            worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
        }
        void releaseOne() throws Exception { worker.submit(pending.remove()).get(10, TimeUnit.SECONDS); }
        @Override public void close() throws Exception { release(); worker.shutdown(); assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    @Test void batchableTailOverlapsTheWritingPrefixButAnEffectWaitsForBoth(@TempDir Path dir) throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        try (var lane = new StepWriter(); var store = new FrontierFileStore(dir, KernelPayloadCodecs.scheduleEffects(), lane,
                clock::get, (delay, wake) -> { })) {
            try {
            assertTrue(store.tryContinueTurn());
            store.append(transaction(1), Durability.BATCHABLE);
            store.yieldTurnAsync(); clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(50));
            assertTrue(store.tryContinueTurn(), "ordinary memory-only continuation need not await a previous fsync");
            assertEquals(1, lane.pending.size(), "one group, not a job per record");
            assertEquals(0, store.flushedSequence(WORLD));
            assertEquals(1, store.pressure().bufferedRecords());
            assertThrows(IllegalStateException.class, () -> store.recover(WORLD), "read-only recovery cannot force or adopt queued bytes");
            assertEquals(List.of(transaction(1)), store.recoverOwned(WORLD).walTail());
            store.prepareAppend(transaction(2), Durability.BATCHABLE).poll().orElseThrow();
            var required = store.prepareAppend(transaction(3), Durability.DURABLE_BEFORE_EFFECT);
            assertTrue(required.poll().isEmpty()); store.yieldTurnAsync();
            assertEquals(0, store.flushedSequence(WORLD)); assertEquals(3, store.pressure().bufferedRecords());
            lane.releaseOne();
            assertTrue(store.tryContinueTurn());
            assertEquals(1, store.flushedSequence(WORLD));
            assertTrue(required.poll().isEmpty(), "the first receipt cannot acknowledge its later tail");
            store.yieldTurnAsync();
            try (var interrupted = new FrontierFileStore(dir, KernelPayloadCodecs.scheduleEffects())) {
                assertEquals(List.of(transaction(1)), interrupted.recover(WORLD).walTail(),
                        "restart may only adopt the actually written prefix");
            }
            lane.releaseOne(); assertTrue(store.tryContinueTurn());
            assertEquals(3, required.poll().orElseThrow().walSequence());
            assertEquals(3, store.flushedSequence(WORLD), "effect permission covers the complete causal prefix");
            store.yieldTurnAsync();
            assertEquals(0, store.pressure().bufferedRecords());
            assertFalse(store.pressure().awaitingDurability());
            } finally { lane.release(); }
        }
        try (var recovered = new FrontierFileStore(dir, KernelPayloadCodecs.scheduleEffects())) {
            assertEquals(List.of(transaction(1), transaction(2), transaction(3)), recovered.recover(WORLD).walTail());
        }
    }
    @Test void boundedLaneBackpressureRetainsBytesAndRetriesWithoutAcknowledgingThem(@TempDir Path dir) {
        var rejecting = new java.util.concurrent.atomic.AtomicBoolean(true);
        Executor lane = task -> { if (rejecting.get()) throw new RejectedExecutionException("bounded lane full"); task.run(); };
        var clock = new java.util.concurrent.atomic.AtomicLong();
        try (var store = new FrontierFileStore(dir, KernelPayloadCodecs.scheduleEffects(), lane, clock::get, (delay, wake) -> { })) {
            assertTrue(store.tryContinueTurn()); store.append(transaction(1), Durability.BATCHABLE); store.yieldTurnAsync();
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(50));
            assertFalse(store.tryContinueTurn());
            assertEquals(1, store.pressure().bufferedRecords()); assertEquals(0, store.flushedSequence(WORLD));
            rejecting.set(false);
            assertTrue(store.tryContinueTurn()); assertEquals(1, store.flushedSequence(WORLD)); store.yieldTurnAsync();
            assertEquals(1, store.pressure().forcedGroups());
        }
    }
    @Test void writingAndBufferedPrefixesShareOneRetentionLimit(@TempDir Path dir) throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        try (var lane = new StepWriter(); var store = new FrontierFileStore(dir, KernelPayloadCodecs.scheduleEffects(), lane,
                clock::get, (delay, wake) -> { })) {
            try {
            assertTrue(store.tryContinueTurn()); store.prepareAppend(transaction(1), Durability.BATCHABLE).poll().orElseThrow();
            store.yieldTurnAsync(); clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(50));
            assertTrue(store.tryContinueTurn());
            for (int i = 2; i <= 64; i++) store.prepareAppend(transaction(i), Durability.BATCHABLE).poll().orElseThrow();
            var blocked = store.prepareAppend(transaction(65), Durability.BATCHABLE);
            assertTrue(blocked.poll().isEmpty()); assertEquals(64, store.pressure().bufferedRecords());
            store.yieldTurnAsync(); assertFalse(store.tryContinueTurn());
            lane.releaseOne(); assertTrue(store.tryContinueTurn());
            assertEquals(65, blocked.poll().orElseThrow().walSequence());
            assertEquals(64, store.pressure().bufferedRecords()); store.yieldTurnAsync();
            assertFalse(store.tryContinueTurn());
            lane.releaseOne(); assertTrue(store.tryContinueTurn()); store.yieldTurnAsync();
            assertEquals(65, store.flushedSequence(WORLD));
            } finally { lane.release(); }
        }
    }
    @Test void failedWriterIsFatalBeforeNextTurnAndNeverAcknowledgesItsSequence(@TempDir Path dir) throws Exception {
        try (var lane = new StepWriter()) {
            var clock = new java.util.concurrent.atomic.AtomicLong();
            var store = new FrontierFileStore(dir, KernelPayloadCodecs.scheduleEffects(), lane, clock::get, (delay, wake) -> { });
            assertTrue(store.tryContinueTurn()); store.append(transaction(1), Durability.BATCHABLE); store.yieldTurnAsync();
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(50)); assertTrue(store.tryContinueTurn());
            store.prepareAppend(transaction(2), Durability.BATCHABLE).poll().orElseThrow(); store.yieldTurnAsync();
            // An invalid exact store-format target creates a real I/O failure in the queued job.
            var world = dir.resolve("frontier-v3/frontier_wal-gate"); Files.createDirectories(world);
            Files.createDirectory(world.resolve("store-format.bin"));
            lane.release();
            assertThrows(IllegalStateException.class, store::tryContinueTurn);
            assertEquals(0, store.flushedSequence(WORLD));
            assertThrows(IllegalStateException.class, store::close);
            assertThrows(IllegalStateException.class, () -> store.append(transaction(2), Durability.BATCHABLE));
        }
    }
}
