package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;
import java.util.Queue;

/** One main-thread drain pass. Retries remain queued until generation/save dependencies can progress. */
public final class FrontierV3UnloadQueuePass {
    private int remaining;

    public void begin(int queuedCallbacks) {
        if (queuedCallbacks < 0) throw new IllegalArgumentException("negative unload queue size");
        remaining = queuedCallbacks;
    }

    public Runnable poll(Queue<Runnable> queue) {
        Objects.requireNonNull(queue, "unload queue");
        if (remaining == 0) return null;
        remaining--;
        return queue.poll();
    }
}
