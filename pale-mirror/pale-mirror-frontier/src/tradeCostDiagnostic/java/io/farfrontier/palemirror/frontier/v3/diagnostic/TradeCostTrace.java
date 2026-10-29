package io.farfrontier.palemirror.frontier.v3.diagnostic;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Supplier;

/** Offline observer only. A disabled span allocates nothing. Nested inclusive
 * totals are never summed as phase cost: phases receive exclusive contributions.
 * Instrumentation overhead remains visible in the observed parent/root. */
public final class TradeCostTrace {
    private static final com.sun.management.ThreadMXBean CPU =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    public record Cost(long calls, long cpu, long selfCpu, long bytes, long selfBytes, long wall) { }
    public record Measurement<T>(T answer, Map<String, Cost> methods, Map<String, Cost> phases, Map<String, Cost> outcomes) { }
    private static final class Counter {
        long calls, cpu, selfCpu, bytes, selfBytes, wall;
        void add(long c, long own, long b, long ownBytes, long w) {
            calls++; cpu += c; selfCpu += own; bytes += b; selfBytes += ownBytes; wall += w;
        }
        Cost image() { return new Cost(calls, cpu, selfCpu, bytes, selfBytes, wall); }
    }
    private static final class Context {
        Scope current;
        int depth;
        final Map<String, Counter> methods = new TreeMap<>(), phases = new TreeMap<>(), outcomes = new TreeMap<>();
        Counter counter(Map<String, Counter> map, String key) {
            if (!map.containsKey(key) && map.size() == 64) throw new IllegalStateException("diagnostic span bound");
            return map.computeIfAbsent(key, ignored -> new Counter());
        }
    }
    public static final class Scope implements AutoCloseable {
        private final Context context;
        private final Scope parent;
        private final String method, phase;
        private final long startCpu, startBytes, startWall;
        private long childCpu, childBytes;
        private Scope(Context context, String method, String phase) {
            this.context = context; this.parent = context.current; this.method = method;
            this.phase = parent != null && !parent.phase.equals("CONTROL") ? parent.phase
                    : phase.isEmpty() ? "CONTROL" : phase;
            if (++context.depth > 32) throw new IllegalStateException("diagnostic stack bound");
            context.current = this;
            startWall = System.nanoTime(); startBytes = CPU.getThreadAllocatedBytes(Thread.currentThread().threadId());
            startCpu = CPU.getCurrentThreadCpuTime();
        }
        public void close() {
            long cpu = CPU.getCurrentThreadCpuTime() - startCpu;
            long bytes = CPU.getThreadAllocatedBytes(Thread.currentThread().threadId()) - startBytes;
            long wall = System.nanoTime() - startWall;
            if (context.current != this) throw new IllegalStateException("unordered diagnostic close");
            long self = cpu - childCpu, ownBytes = bytes - childBytes;
            if (self < 0 || ownBytes < 0) throw new IllegalStateException("invalid diagnostic accounting");
            context.counter(context.methods, method).add(cpu, self, bytes, ownBytes, wall);
            context.counter(context.phases, phase).add(self, self, ownBytes, ownBytes, 0);
            if (parent != null) { parent.childCpu += cpu; parent.childBytes += bytes; }
            context.current = parent; context.depth--;
        }
    }
    public static Scope enter(String method, String phase) {
        var context = ACTIVE.get();
        return context == null ? null : new Scope(context, method, phase);
    }
    public static void count(String outcome) {
        var context = ACTIVE.get();
        if (context != null) context.counter(context.outcomes, outcome).calls++;
    }
    public static boolean flag(String outcome, boolean value) {
        count(outcome + (value ? ":true" : ":false")); return value;
    }
    public static <T> Measurement<T> measure(Supplier<T> calculation) {
        if (ACTIVE.get() != null) throw new IllegalStateException("nested diagnostic root");
        var context = new Context(); ACTIVE.set(context);
        T result;
        try (var root = enter("decision", "CONTROL")) { result = calculation.get(); }
        finally { ACTIVE.remove(); }
        var methods = new TreeMap<String, Cost>(); var phases = new TreeMap<String, Cost>(); var outcomes = new TreeMap<String, Cost>();
        context.methods.forEach((key, value) -> methods.put(key, value.image()));
        context.phases.forEach((key, value) -> phases.put(key, value.image()));
        context.outcomes.forEach((key, value) -> outcomes.put(key, value.image()));
        long cpu = phases.values().stream().mapToLong(Cost::cpu).sum();
        long bytes = phases.values().stream().mapToLong(Cost::bytes).sum();
        if (cpu != methods.get("decision").cpu() || bytes != methods.get("decision").bytes())
            throw new AssertionError("phase partition differs from observed root");
        return new Measurement<>(result, Map.copyOf(methods), Map.copyOf(phases), Map.copyOf(outcomes));
    }
    private TradeCostTrace() { }
}
