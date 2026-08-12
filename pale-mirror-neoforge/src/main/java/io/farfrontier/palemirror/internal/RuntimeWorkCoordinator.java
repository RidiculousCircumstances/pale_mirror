package io.farfrontier.palemirror.internal;

import io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** One admission controller and one telemetry surface for all PM server-thread work. */
public final class RuntimeWorkCoordinator {
    public enum Priority { CRITICAL, NORMAL, BACKGROUND }

    private final Map<String, Metric> metrics = new LinkedHashMap<>();
    private long tickStarted;
    private long softBudgetNanos;
    private int weightBudget;
    private int admittedWeight;
    private int deferred;
    private long ticks;
    private long overruns;
    private long lastElapsedNanos;
    private boolean overrunRecorded;

    public void beginTick() {
        tickStarted = System.nanoTime();
        softBudgetNanos = Math.max(1L, (long) (PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000D));
        weightBudget = PaleMirrorServerConfig.RUNTIME_WEIGHT_BUDGET.get();
        admittedWeight = 0;
        deferred = 0;
        overrunRecorded = false;
        ticks++;
    }

    public boolean run(String name, int weight, Priority priority, BooleanSupplier work) {
        if (weight < 1) throw new IllegalArgumentException("Work weight must be positive");
        long elapsed = System.nanoTime() - tickStarted;
        boolean exhausted = admittedWeight + weight > weightBudget || elapsed >= softBudgetNanos;
        if (priority != Priority.CRITICAL && exhausted) {
            metrics.computeIfAbsent(name, ignored -> new Metric()).deferred++;
            deferred++;
            return false;
        }
        admittedWeight += weight;
        long started = System.nanoTime();
        boolean changed = work.getAsBoolean();
        long duration = System.nanoTime() - started;
        Metric metric = metrics.computeIfAbsent(name, ignored -> new Metric());
        metric.runs++;
        metric.nanos += duration;
        metric.maxNanos = Math.max(metric.maxNanos, duration);
        if (!overrunRecorded && System.nanoTime() - tickStarted > softBudgetNanos) {
            overrunRecorded = true;
            overruns++;
        }
        return changed;
    }

    public void finishTick() { lastElapsedNanos = Math.max(0L, System.nanoTime() - tickStarted); }

    public boolean due(long gameTick, String key, int interval) {
        if (interval < 1) throw new IllegalArgumentException("Interval must be positive");
        return Math.floorMod(gameTick + Integer.toUnsignedLong(key.hashCode()), interval) == 0;
    }

    public String summary() {
        return "runtimeMs=" + round(lastElapsedNanos / 1_000_000D) + "/" + round(softBudgetNanos / 1_000_000D)
                + ", weight=" + admittedWeight + "/" + weightBudget + ", deferred=" + deferred
                + ", overrunTicks=" + overruns + "/" + ticks;
    }

    public String detailedSummary() {
        StringBuilder result = new StringBuilder(summary());
        metrics.forEach((name, metric) -> result.append("\n").append(name).append(": runs=").append(metric.runs)
                .append(", deferred=").append(metric.deferred).append(", avgMs=")
                .append(round(metric.runs == 0 ? 0D : metric.nanos / 1_000_000D / metric.runs))
                .append(", maxMs=").append(round(metric.maxNanos / 1_000_000D)));
        return result.toString();
    }

    private static double round(double value) { return Math.round(value * 1000D) / 1000D; }

    private static final class Metric {
        private long runs;
        private long deferred;
        private long nanos;
        private long maxNanos;
    }
}
