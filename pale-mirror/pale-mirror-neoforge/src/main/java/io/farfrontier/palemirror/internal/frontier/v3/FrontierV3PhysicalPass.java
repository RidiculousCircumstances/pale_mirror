package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/** One physical round per native tick; ready callbacks may only finish its suffix. */
final class FrontierV3PhysicalPass {
    private final FrontierV3CooperativeStageCursor cursor = new FrontierV3CooperativeStageCursor();
    private final LongSupplier clock;
    private long tick = Long.MIN_VALUE, spent, activeStarted;
    private boolean completed, running;
    FrontierV3PhysicalPass(LongSupplier clock) { this.clock = java.util.Objects.requireNonNull(clock); }
    boolean begin(long nativeTick) {
        if (tick == nativeTick) return false;
        if (running) throw new IllegalStateException("physical tick changed inside its pass");
        tick = nativeTick; spent = 0; completed = false;
        return true;
    }
    boolean current(long nativeTick) { return tick == nativeTick; }
    long elapsed() { return spent + (running ? clock.getAsLong() - activeStarted : 0); }
    void run(int stages, long allowance, BooleanSupplier admitted, IntConsumer execute) {
        if (completed || spent >= allowance) return;
        if (running) throw new IllegalStateException("reentrant physical pass");
        activeStarted = clock.getAsLong(); running = true;
        try { completed = cursor.run(stages, admitted, () -> elapsed() < allowance, execute); }
        finally { spent = elapsed(); running = false; }
    }
}
