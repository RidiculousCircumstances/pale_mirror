package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Owner-thread delivery of exact journal receipts. Workers never run callbacks.
 * Disposable: durable protocol records, not this queue, own recovery. */
final class FrontierV3DurableContinuations {
    private record Pending(CompletableFuture<Long> receipt, Runnable continuation) { }
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    void add(CompletableFuture<Long> receipt, Runnable continuation) {
        if (pending.size() >= 8192) throw new IllegalStateException("physical durable continuation capacity exhausted");
        pending.addLast(new Pending(Objects.requireNonNull(receipt), Objects.requireNonNull(continuation)));
    }
    void advance() { advance(64); }
    void finish() {
        advance(pending.size());
        if (!pending.isEmpty()) throw new IllegalStateException("physical continuations precede completed journal flush");
    }
    private void advance(int limit) {
        int count = Math.min(limit, pending.size());
        for (int i = 0; i < count; i++) {
            var next = pending.getFirst();
            if (!next.receipt().isDone()) { pending.removeFirst(); pending.addLast(next); continue; }
            next.receipt().join(); // Completed only; failures keep the obligation and fail visibly.
            pending.removeFirst();
            next.continuation().run();
        }
    }
}
