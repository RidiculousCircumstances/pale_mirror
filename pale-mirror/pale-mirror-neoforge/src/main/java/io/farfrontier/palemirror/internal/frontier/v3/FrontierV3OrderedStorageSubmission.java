package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Orders admission to a native storage worker behind external durable prerequisites.
 * Only submission is sequenced: native IO can still batch/coalesce its own operations.
 * No server callback is needed to drain this prefix during native flush/shutdown. */
public final class FrontierV3OrderedStorageSubmission {
    private CompletableFuture<Void> submitted = CompletableFuture.completedFuture(null);

    public synchronized <T> CompletableFuture<T> submit(CompletableFuture<?> prerequisite,
            Supplier<CompletableFuture<T>> operation) {
        Objects.requireNonNull(prerequisite); Objects.requireNonNull(operation);
        var admission = submitted.thenCombine(prerequisite, (previous, ready) ->
                Objects.requireNonNull(operation.get()));
        // A failed prerequisite prevents later reads/syncs from falsely proving the
        // skipped write. Actual native IO failures retain their own returned future.
        submitted = admission.thenApply(ignored -> null);
        return admission.thenCompose(result -> result);
    }

    public <T> CompletableFuture<T> submit(Supplier<CompletableFuture<T>> operation) {
        return submit(CompletableFuture.completedFuture(null), operation);
    }

    /** Shutdown only. Ordinary ticks never wait on this future. */
    public synchronized CompletableFuture<Void> submittedPrefix() { return submitted; }
}
