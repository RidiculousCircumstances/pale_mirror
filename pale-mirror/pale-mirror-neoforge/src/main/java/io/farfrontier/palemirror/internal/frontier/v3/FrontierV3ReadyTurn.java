package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coalesces worker readiness onto an always-queued owner executor. A notification
 * carries no state or execution permission; the owner rechecks both on arrival. */
final class FrontierV3ReadyTurn implements AutoCloseable {
    private final Executor ownerQueue;
    private final Runnable resume;
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile boolean closed;
    private volatile long queuedAt, lastQueueDelayNanos, maxQueueDelayNanos, delivered;
    String diagnostic() {
        return "{\"delivered\":" + delivered + ",\"lastDelayNanos\":" + lastQueueDelayNanos
                + ",\"maxDelayNanos\":" + maxQueueDelayNanos + ",\"pendingAgeNanos\":"
                + (queued.get() && queuedAt != 0L ? Math.max(0L, System.nanoTime() - queuedAt) : 0L) + "}";
    }

    FrontierV3ReadyTurn(Executor ownerQueue, Runnable resume) {
        this.ownerQueue = Objects.requireNonNull(ownerQueue);
        this.resume = Objects.requireNonNull(resume);
    }
    void signal() {
        if (closed || !queued.compareAndSet(false, true)) return;
        long requestedAt = System.nanoTime(); queuedAt = requestedAt;
        try {
            ownerQueue.execute(() -> {
                queued.set(false);
                lastQueueDelayNanos = Math.max(0L, System.nanoTime() - requestedAt);
                maxQueueDelayNanos = Math.max(maxQueueDelayNanos, lastQueueDelayNanos); delivered++;
                if (!closed) resume.run();
            });
        } catch (RuntimeException failure) { queued.set(false); throw failure; }
    }
    @Override public void close() { closed = true; }
}
