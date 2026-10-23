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

    FrontierV3ReadyTurn(Executor ownerQueue, Runnable resume) {
        this.ownerQueue = Objects.requireNonNull(ownerQueue);
        this.resume = Objects.requireNonNull(resume);
    }
    void signal() {
        if (closed || !queued.compareAndSet(false, true)) return;
        try {
            ownerQueue.execute(() -> {
                queued.set(false);
                if (!closed) resume.run();
            });
        } catch (RuntimeException failure) { queued.set(false); throw failure; }
    }
    @Override public void close() { closed = true; }
}
