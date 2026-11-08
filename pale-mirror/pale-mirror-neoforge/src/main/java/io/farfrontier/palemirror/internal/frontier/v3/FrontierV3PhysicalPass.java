package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/** Continuous ordered rounds; each native tick admits at most one invocation of
 * each stage. Ready callbacks share that tick's work count and measured cost.
 * The registry's preparation admission spends the soft budget; it must not
 * suppress safety checks or the finite contact window of an admitted effect. */
final class FrontierV3PhysicalPass {
    private final LongSupplier clock;
    private long tick = Long.MIN_VALUE, spent, activeStarted;
    private int next, visited;
    private boolean running;
    FrontierV3PhysicalPass(LongSupplier clock) { this.clock = java.util.Objects.requireNonNull(clock); }
    boolean begin(long nativeTick) {
        if (tick == nativeTick) return false;
        if (running) throw new IllegalStateException("physical tick changed inside its pass");
        tick = nativeTick; spent = 0; visited = 0;
        return true;
    }
    boolean current(long nativeTick) { return tick == nativeTick; }
    long elapsed() { return spent + (running ? clock.getAsLong() - activeStarted : 0); }
    void run(int stages, BooleanSupplier admitted, IntConsumer execute) {
        if (visited >= stages) return;
        if (running) throw new IllegalStateException("reentrant physical pass");
        activeStarted = clock.getAsLong(); running = true;
        try {
            // Completing an older suffix must not discard this tick's remaining
            // invocations. Continue in global dependency order, stopping before
            // any executor could run twice in this native tick.
            while (visited < stages && admitted.getAsBoolean()) {
                execute.accept(next);
                next = (next + 1) % stages;
                visited++;
            }
        }
        finally { spent = elapsed(); running = false; }
    }
}
