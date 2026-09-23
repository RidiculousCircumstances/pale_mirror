package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3EntitySaveBatchTest {
    @Test void deferredReadBarrierWaitsForAllObservationsAndPreservesFailure() {
        var batch = new FrontierV3EntitySaveBatch();
        assertTrue(batch.pendingReadBarrier().isEmpty());
        var a = new CompletableFuture<Void>(); var b = new CompletableFuture<Void>();
        batch.recordRead(1, a); batch.recordRead(2, b);
        var barrier = batch.pendingReadBarrier().orElseThrow();
        a.complete(null); assertFalse(barrier.isDone());
        b.completeExceptionally(new IOException("read failed"));
        assertThrows(CompletionException.class, barrier::join);
        assertTrue(batch.pendingReadBarrier().isEmpty(), "failed completed read must not cause a retry loop");
        var ticket = batch.completePass(true, () -> fail("failed observation cannot synchronize")).orElseThrow();
        assertThrows(CompletionException.class, ticket.saved()::join);
    }

    @Test void storedReadWaitsForServerObservationAndSyncWithoutInventingAWrite() {
        var batch = new FrontierV3EntitySaveBatch(); var observed = new CompletableFuture<Void>();
        var read = batch.recordRead(1, observed);
        assertTrue(batch.current(read));
        assertTrue(batch.completePass(true, () -> fail("server observation not processed")).isEmpty());
        observed.complete(null);
        var sync = new CompletableFuture<Void>();
        var ticket = batch.completePass(true, () -> sync).orElseThrow();
        assertFalse(batch.accept(ticket)); sync.complete(null); assertTrue(batch.accept(ticket));
        assertFalse(batch.current(read));
    }

    @Test void laterWriteInvalidatesReadButDifferentChunkDoesNotDiscardItsObservation() {
        var batch = new FrontierV3EntitySaveBatch(); var observed = new CompletableFuture<Void>();
        var read = batch.recordRead(1, observed);
        batch.record(2, CompletableFuture.completedFuture(null)); assertTrue(batch.current(read));
        batch.record(1, CompletableFuture.completedFuture(null)); assertFalse(batch.current(read));
        var ticket = batch.completePass(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertTrue(batch.accept(ticket), "superseded read must not hold the newer exact writes");
    }

    @Test void failedReadCannotAcknowledgeAndExactRereadCanRecover() {
        var batch = new FrontierV3EntitySaveBatch();
        batch.recordRead(1, CompletableFuture.failedFuture(new IOException("read failed")));
        var failed = batch.completePass(true, () -> fail("failed read is not absence")).orElseThrow();
        assertThrows(CompletionException.class, failed.saved()::join); assertFalse(batch.accept(failed));
        batch.recordRead(1, CompletableFuture.completedFuture(null));
        var recovered = batch.completePass(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertTrue(batch.accept(recovered));
    }

    @Test void successfulStorageReadCannotHideAPreviousFailedWrite() {
        var batch = new FrontierV3EntitySaveBatch();
        batch.record(1, CompletableFuture.failedFuture(new IOException("write failed")));
        // IOWorker reads may return pending data, not necessarily a disk read.
        batch.recordRead(1, CompletableFuture.completedFuture(null));
        var ticket = batch.completePass(true, () -> fail("read cannot waive exact write failure")).orElseThrow();
        assertThrows(CompletionException.class, ticket.saved()::join);
        assertFalse(batch.accept(ticket));
    }

    @Test void newChunkSuccessDoesNotHideOldChunkFailureAndExactRewriteRepairsTheBatch() {
        var batch = new FrontierV3EntitySaveBatch(); var syncs = new AtomicInteger();
        batch.record(1, CompletableFuture.failedFuture(new IOException("old chunk failed")));
        batch.record(2, CompletableFuture.completedFuture(null));
        var failed = batch.completePass(true, () -> { syncs.incrementAndGet(); return CompletableFuture.completedFuture(null); }).orElseThrow();
        assertThrows(CompletionException.class, failed.saved()::join);
        assertEquals(0, syncs.get()); assertFalse(batch.accept(failed));
        batch.record(1, CompletableFuture.completedFuture(null));
        var repaired = batch.completePass(true, () -> { syncs.incrementAndGet(); return CompletableFuture.completedFuture(null); }).orElseThrow();
        assertDoesNotThrow(repaired.saved()::join); assertEquals(1, syncs.get());
        assertTrue(batch.accept(repaired)); assertFalse(batch.accept(repaired));
    }

    @Test void allWritesMustCompleteBeforeOneSyncAndSkippedChunksCannotCloseThePass() {
        var batch = new FrontierV3EntitySaveBatch();
        var first = new CompletableFuture<Void>(); var second = new CompletableFuture<Void>();
        var sync = new CompletableFuture<Void>(); var syncs = new AtomicInteger();
        batch.record(1, first); batch.record(2, second);
        assertTrue(batch.completePass(false, () -> fail("incomplete pass cannot synchronize")).isEmpty());
        var ticket = batch.completePass(true, () -> { syncs.incrementAndGet(); return sync; }).orElseThrow();
        first.complete(null); assertEquals(0, syncs.get()); assertFalse(batch.accept(ticket));
        second.complete(null); assertEquals(1, syncs.get()); assertFalse(batch.accept(ticket));
        sync.complete(null); assertTrue(batch.accept(ticket));
    }

    @Test void laterWriteInvalidatesPreviouslyCompletedEvidenceEvenInAnotherChunk() {
        var batch = new FrontierV3EntitySaveBatch();
        batch.record(1, CompletableFuture.completedFuture(null));
        var ticket = batch.completePass(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        batch.record(2, CompletableFuture.completedFuture(null));
        assertFalse(batch.current(ticket)); assertFalse(batch.accept(ticket));
        var next = batch.completePass(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertTrue(batch.accept(next));
    }

    @Test void exceedingBoundNeverEvictsAnUnresolvedWriteAndClaimsSuccess() {
        var batch = new FrontierV3EntitySaveBatch();
        for (int i = 0; i <= FrontierV3EntitySaveBatch.MAX_CHUNKS; i++) batch.record(i, CompletableFuture.completedFuture(null));
        assertTrue(batch.overflowed());
        assertTrue(batch.completePass(true, () -> fail("overflowed observation cannot acknowledge")).isEmpty());
    }
}
