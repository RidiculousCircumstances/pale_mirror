package io.farfrontier.palemirror.frontier.v3.model;

/** Sparse fixed-point labour clock. Travel and suspension never accrue labour. */
public record WorkProgress(long requiredMilliWork, long completedMilliWork,
                           long evaluatedAtTick, int ratePermille, long activeUntilTick) {
    public WorkProgress {
        if (requiredMilliWork < 1 || requiredMilliWork > 1_000_000_000L
                || completedMilliWork < 0 || completedMilliWork > requiredMilliWork
                || evaluatedAtTick < 0 || ratePermille < 0 || ratePermille > 100_000
                || activeUntilTick < evaluatedAtTick
                || ratePermille == 0 && activeUntilTick != evaluatedAtTick)
            throw new IllegalArgumentException("invalid bounded labour clock");
    }
    public static WorkProgress pending(long units, long tick) {
        return new WorkProgress(Math.multiplyExact(units, 1_000L), 0, tick, 0, tick);
    }
    public boolean complete() { return completedMilliWork == requiredMilliWork; }
    public boolean running() { return ratePermille > 0; }
    public long completedAt(long tick) {
        if (tick < evaluatedAtTick) throw new IllegalArgumentException("labour clock cannot run backwards");
        long elapsed = Math.min(tick, activeUntilTick) - evaluatedAtTick;
        long remaining = requiredMilliWork - completedMilliWork;
        return completedMilliWork + Math.min(remaining, Math.multiplyExact(elapsed, ratePermille));
    }
    public WorkProgress pause(long tick) {
        return new WorkProgress(requiredMilliWork, completedAt(tick), tick, 0, tick);
    }
    public WorkProgress resume(long tick, int speedPermille, long permittedUntil) {
        if (running() || complete() || tick < evaluatedAtTick || speedPermille < 1 || permittedUntil <= tick)
            throw new IllegalArgumentException("labour interval needs paused incomplete work and a positive rate");
        long finish = Math.addExact(tick, Math.ceilDiv(requiredMilliWork - completedMilliWork, speedPermille));
        return new WorkProgress(requiredMilliWork, completedMilliWork, tick, speedPermille, Math.min(finish, permittedUntil));
    }
}
