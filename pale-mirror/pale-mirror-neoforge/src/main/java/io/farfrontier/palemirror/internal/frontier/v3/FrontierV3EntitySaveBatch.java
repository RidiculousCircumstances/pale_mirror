package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Server-thread batch inventory: a failure in an old chunk cannot be hidden by a new chunk. */
final class FrontierV3EntitySaveBatch {
    static final int MAX_CHUNKS = 16_384;
    private final Map<Long, CompletableFuture<Void>> writes = new HashMap<>();
    private final Map<Long, CompletableFuture<Void>> reads = new HashMap<>();
    private long generation;
    private boolean overflowed;

    void record(long chunk, CompletableFuture<Void> written) {
        Objects.requireNonNull(written, "entity write");
        generation = Math.incrementExact(generation);
        if (!writes.containsKey(chunk) && !reads.containsKey(chunk) && writes.size() + reads.size() >= MAX_CHUNKS) { overflowed = true; return; }
        // A later exact write of the same chunk replaces the earlier physical state.
        writes.put(chunk, written);
        reads.remove(chunk);
    }

    ReadTicket recordRead(long chunk, CompletableFuture<Void> observedOnServerThread) {
        Objects.requireNonNull(observedOnServerThread, "stored read observation");
        generation = Math.incrementExact(generation);
        if (!reads.containsKey(chunk) && writes.size() + reads.size() >= MAX_CHUNKS) overflowed = true;
        if (!overflowed) reads.put(chunk, observedOnServerThread);
        return new ReadTicket(chunk, observedOnServerThread);
    }

    boolean current(ReadTicket ticket) { return !overflowed && reads.get(ticket.chunk()) == ticket.observed(); }

    Optional<CompletableFuture<Void>> pendingReadBarrier() {
        if (overflowed) return Optional.empty();
        var pending = reads.values().stream().filter(value -> !value.isDone()).toArray(CompletableFuture[]::new);
        return pending.length == 0 ? Optional.empty() : Optional.of(CompletableFuture.allOf(pending));
    }

    Optional<Ticket> completePass(boolean complete, Supplier<CompletableFuture<Void>> synchronize) {
        // Candidate selection must happen before completing the pass, not on an IO callback afterward.
        if (!complete || overflowed || writes.isEmpty() && reads.isEmpty()
                || reads.values().stream().anyMatch(read -> !read.isDone())) return Optional.empty();
        var successfulWrites = CompletableFuture.allOf(java.util.stream.Stream.concat(writes.values().stream(), reads.values().stream())
                .toArray(CompletableFuture[]::new));
        return Optional.of(new Ticket(generation,
                FrontierV3CargoCleanupPersistence.afterSuccessfulWriteAndSync(successfulWrites, synchronize)));
    }

    boolean current(Ticket ticket) { return !overflowed && ticket.generation() == generation; }

    boolean accept(Ticket ticket) {
        if (!current(ticket) || !ticket.saved().isDone() || ticket.saved().isCompletedExceptionally()) return false;
        writes.clear();
        reads.clear();
        generation = Math.incrementExact(generation);
        return true;
    }

    boolean overflowed() { return overflowed; }
    String diagnostic() {
        return "generation=" + generation + ",writes=" + writes.size() + ",reads=" + reads.size()
                + ",pendingReads=" + reads.values().stream().filter(value -> !value.isDone()).count()
                + ",failedWrites=" + writes.values().stream().filter(CompletableFuture::isCompletedExceptionally).count()
                + ",overflow=" + overflowed;
    }
    record Ticket(long generation, CompletableFuture<Void> saved) { }
    record ReadTicket(long chunk, CompletableFuture<Void> observed) { }
}
