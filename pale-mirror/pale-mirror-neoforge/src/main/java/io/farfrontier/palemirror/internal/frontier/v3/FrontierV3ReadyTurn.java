package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/** Coalesces worker readiness onto an always-queued owner executor. A notification
 * carries no state or execution permission; the owner rechecks both on arrival. */
final class FrontierV3ReadyTurn implements AutoCloseable {
    private final Executor ownerQueue;
    private final Runnable resume;
    private final String traceWorld;
    private final AtomicReference<FrontierV3DelayTrace.Task> queued = new AtomicReference<>();
    private volatile boolean closed;
    private volatile long queuedAt, lastQueueDelayNanos, maxQueueDelayNanos, totalQueueDelayNanos, delivered;
    String diagnostic() {
        return "{\"delivered\":" + delivered + ",\"lastDelayNanos\":" + lastQueueDelayNanos
                + ",\"maxDelayNanos\":" + maxQueueDelayNanos + ",\"totalDelayNanos\":" + totalQueueDelayNanos + ",\"pendingAgeNanos\":"
                + (queued.get() != null && queuedAt != 0L ? Math.max(0L, System.nanoTime() - queuedAt) : 0L) + "}";
    }

    FrontierV3ReadyTurn(Executor ownerQueue, Runnable resume) { this("unbound", ownerQueue, resume); }
    FrontierV3ReadyTurn(String world, Executor ownerQueue, Runnable resume) {
        traceWorld = Objects.requireNonNull(world);
        this.ownerQueue = Objects.requireNonNull(ownerQueue);
        this.resume = Objects.requireNonNull(resume);
    }
    void signal() {
        if (closed) return;
        var trace = FrontierV3DelayTrace.task("READY_TURN", traceWorld);
        var prior = queued.compareAndExchange(null, trace);
        if (prior != null) { prior.mark("COALESCED"); return; }
        long requestedAt = System.nanoTime(); queuedAt = requestedAt;
        trace.mark("QUEUED");
        try {
            ownerQueue.execute(() -> {
                queued.set(null);
                lastQueueDelayNanos = Math.max(0L, System.nanoTime() - requestedAt);
                maxQueueDelayNanos = Math.max(maxQueueDelayNanos, lastQueueDelayNanos);
                totalQueueDelayNanos += lastQueueDelayNanos; delivered++;
                trace.mark("DELIVERED");
                try { if (!closed) trace.deliver(resume); }
                finally { trace.mark("FINISHED"); }
            });
        } catch (RuntimeException failure) { trace.mark("FAILED"); queued.set(null); throw failure; }
    }
    @Override public void close() { closed = true; }
}
