package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OrderedStorageSubmissionTest {
    @Test void nativeReadAndFlushCannotOvertakeAWriteWaitingForItsJournal() {
        var storage = new FrontierV3OrderedStorageSubmission();
        var journal = new CompletableFuture<Void>();
        var writeReceipt = new CompletableFuture<Void>();
        var order = new ArrayList<String>();
        var write = storage.submit(journal, () -> { order.add("write"); return writeReceipt; });
        var read = storage.submit(() -> { order.add("read"); return CompletableFuture.completedFuture("new image"); });
        var flush = storage.submit(() -> { order.add("flush"); return writeReceipt; });
        assertTrue(order.isEmpty()); assertFalse(read.isDone()); assertFalse(flush.isDone());
        journal.complete(null);
        assertEquals(List.of("write", "read", "flush"), order);
        assertEquals("new image", read.join());
        assertFalse(write.isDone()); assertFalse(flush.isDone());
        assertTrue(storage.submittedPrefix().isDone());
        writeReceipt.complete(null);
        write.join(); flush.join();
    }

    @Test void failedJournalCannotPublishAnEntityOrProduceASuccessfulAbsenceProof() {
        var storage = new FrontierV3OrderedStorageSubmission();
        var journal = new CompletableFuture<Void>();
        var called = new ArrayList<String>();
        var write = storage.submit(journal, () -> { called.add("write"); return CompletableFuture.completedFuture(null); });
        var read = storage.submit(() -> { called.add("read"); return CompletableFuture.completedFuture("absent"); });
        var flush = storage.submit(() -> { called.add("flush"); return CompletableFuture.completedFuture(null); });
        var failure = new IllegalStateException("journal force failed");
        journal.completeExceptionally(failure);
        for (var result : List.of(write, read, flush, storage.submittedPrefix()))
            assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
        assertTrue(called.isEmpty());
    }

    @Test void laterReadyWritesStillWaitForEarlierAdmissionButKeepNativeBatching() {
        var storage = new FrontierV3OrderedStorageSubmission();
        var firstJournal = new CompletableFuture<Void>();
        var secondJournal = new CompletableFuture<Void>();
        var nativeReceipt = new CompletableFuture<Void>();
        var order = new ArrayList<Integer>();
        storage.submit(firstJournal, () -> { order.add(1); return nativeReceipt; });
        var second = storage.submit(secondJournal, () -> { order.add(2); return nativeReceipt; });
        secondJournal.complete(null);
        assertTrue(order.isEmpty());
        firstJournal.complete(null);
        assertEquals(List.of(1, 2), order); // Both admitted before either native IO completes.
        assertFalse(second.isDone());
        nativeReceipt.completeExceptionally(new IllegalStateException("native IO failed"));
        assertThrows(CompletionException.class, second::join);
    }
}
