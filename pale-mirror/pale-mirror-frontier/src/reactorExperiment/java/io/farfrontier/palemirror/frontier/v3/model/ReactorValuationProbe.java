package io.farfrontier.palemirror.frontier.v3.model;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded, disposable experiment adapter. No Reactor type escapes its request API.
 * The host still owns basis capture, scheduling capacity and publication freshness. */
final class ReactorValuationProbe<V> implements AutoCloseable {
    enum Status { VALUE, WAITING_CAPACITY, STALE, CLOSED }
    record Reply<V>(Status status, V value, long witness) { }
    private record Entry<V>(ValuationExperimentInputs.Basis basis, Object invocation, Mono<V> value) { }
    private final LinkedHashMap<ValuationExperimentInputs.Query, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private final Map<ValuationExperimentInputs.Query, Long> latest = new HashMap<>();
    private final Semaphore work;
    private final int keys, subscribers;
    private final Scheduler scheduler;
    private boolean closed;
    private int pending, running, peakPending, peakRunning, peakKeys;
    private long calls, shared, stale, waiting;

    ReactorValuationProbe(int keys, int work, int subscribers, Scheduler scheduler) {
        if (keys < 1 || work < 1 || subscribers < 1) throw new IllegalArgumentException("positive experiment bounds required");
        this.keys = keys; this.work = new Semaphore(work); this.subscribers = subscribers; this.scheduler = scheduler;
    }
    synchronized CompletableFuture<Reply<V>> request(ValuationExperimentInputs.Basis basis, long witness, Supplier<V> compute) {
        if (closed) return CompletableFuture.completedFuture(new Reply<>(Status.CLOSED, null, witness));
        var query = basis.query(); var entry = entries.get(query);
        if (entry != null && !entry.basis().equals(basis)) {
            entries.remove(query); latest.remove(query); entry = null;
        }
        if (entry != null) latest.put(query, witness);
        if (pending >= subscribers) return waitReply(witness);
        if (entry == null) {
            if (!work.tryAcquire()) return waitReply(witness);
            calls++; running++; peakRunning = Math.max(peakRunning, running);
            // cache keeps an already running computation alive after an observer leaves.
            // Its work permit is released by the computation, never by cancellation.
            var computation = new AtomicReference<>(compute);
            var invocation = new Object();
            Mono<V> task = Mono.fromCallable(() -> Objects.requireNonNull(computation.getAndSet(null).get()));
            if (scheduler != null) task = task.subscribeOn(scheduler);
            var next = new Entry<V>(basis, invocation, task.doOnSuccess(ignored -> finished()).doOnError(failure -> {
                computation.set(null);
                finished();
                synchronized (this) {
                    var failed = entries.get(query);
                    if (failed != null && failed.invocation() == invocation) { entries.remove(query); latest.remove(query); }
                }
            }).cache());
            entries.put(query, next);
            while (entries.size() > keys) {
                var eldest = entries.keySet().iterator().next(); entries.remove(eldest); latest.remove(eldest);
            }
            entry = next; peakKeys = Math.max(peakKeys, entries.size());
        } else shared++;
        latest.put(query, witness); pending++; peakPending = Math.max(peakPending, pending);
        var selected = entry;
        var source = entry.value().toFuture();
        var result = source.handle((value, failure) -> {
            synchronized (this) {
                if (failure != null) {
                    if (entries.get(query) == selected) { entries.remove(query); latest.remove(query); }
                    throw new CompletionException(failure); // explicit caller-visible failure, no empty success/retry
                }
                boolean current = !closed && entries.get(query) == selected && Objects.equals(latest.get(query), witness);
                if (!current) stale++;
                return new Reply<>(current ? Status.VALUE : Status.STALE, current ? value : null, witness);
            }
        });
        // Even cancelled observer futures stop retaining a subscriber slot; cache owns
        // any still-running task and its independent permit until actual completion.
        result.whenComplete((ignored, failed) -> {
            if (result.isCancelled()) source.cancel(true);
            synchronized (this) { pending--; }
        });
        return result;
    }
    private CompletableFuture<Reply<V>> waitReply(long witness) {
        waiting++; return CompletableFuture.completedFuture(new Reply<>(Status.WAITING_CAPACITY, null, witness));
    }
    private synchronized void finished() { running--; work.release(); }
    synchronized long calls() { return calls; }
    synchronized String statistics() {
        return "{\"calls\":" + calls + ",\"shared\":" + shared + ",\"stale\":" + stale + ",\"waiting\":" + waiting
                + ",\"keys\":" + entries.size() + ",\"pending\":" + pending + ",\"running\":" + running
                + ",\"peakKeys\":" + peakKeys + ",\"peakPending\":" + peakPending + ",\"peakRunning\":" + peakRunning + "}";
    }
    @Override public synchronized void close() { closed = true; entries.clear(); latest.clear(); }
}
