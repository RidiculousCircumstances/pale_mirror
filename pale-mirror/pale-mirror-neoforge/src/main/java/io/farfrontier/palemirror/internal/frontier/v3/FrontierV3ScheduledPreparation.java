package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Bounded snapshot workers. Exact-predecessor preparation never queues behind
 * independent forecasts/checkpoints; the kernel alone owns publication. */
final class FrontierV3ScheduledPreparation<S> implements ScheduledActionPlanner<S>, AutoCloseable {
    private final ScheduledActionPlanner<S> delegate;
    private final String traceWorld;
    private final java.util.concurrent.atomic.AtomicLong completed = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong totalNanos = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong maxNanos = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong adviceCompleted = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong adviceNanos = new java.util.concurrent.atomic.AtomicLong();
    private long adviceAccepted, adviceRejected;
    private final Map<String, Long> adviceRejectionReasons = new TreeMap<>();
    private long cancelled;
    private long cancelledReady;
    private FrontierV3DelayTrace.Task exactTrace = FrontierV3DelayTrace.Task.OFF;
    long pendingTraceId(ScheduledAction action, long revision) { return exactTrace.matches(action, revision) ? exactTrace.id : 0; }
    private volatile Runnable ready = () -> { };
    void onReady(Runnable listener) { ready = Objects.requireNonNull(listener); }
    String diagnostic(boolean pending) {
        return "{\"pending\":" + pending + ",\"running\":" + (worker.getActiveCount() + exactWorker.getActiveCount())
                + ",\"queued\":" + (worker.getQueue().size() + exactWorker.getQueue().size()) + ",\"completed\":" + completed.get()
                + ",\"cancelled\":" + cancelled + ",\"cancelledReady\":" + cancelledReady + ",\"totalNanos\":" + totalNanos.get() + ",\"maxNanos\":" + maxNanos.get()
                + ",\"adviceWorkers\":" + worker.getCorePoolSize()
                + ",\"adviceCompleted\":" + adviceCompleted.get() + ",\"adviceNanos\":" + adviceNanos.get()
                + ",\"adviceAccepted\":" + adviceAccepted + ",\"adviceRejected\":" + adviceRejected
                + ",\"adviceRejectionReasons\":" + new com.google.gson.Gson().toJson(adviceRejectionReasons) + "}";
    }
    private final ThreadPoolExecutor worker;
    private final ThreadPoolExecutor exactWorker = worker("PMV3-scheduled-preparation", 1, 1);
    private static ThreadPoolExecutor worker(String name, int threads, int capacity) {
        var sequence = new java.util.concurrent.atomic.AtomicInteger();
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), task -> {
                    var thread = new Thread(task, threads == 1 ? name : name + "-" + sequence.getAndIncrement());
                    thread.setDaemon(true); return thread;
                });
    }
    FrontierV3ScheduledPreparation(ScheduledActionPlanner<S> delegate) { this(delegate, "unbound"); }
    FrontierV3ScheduledPreparation(ScheduledActionPlanner<S> delegate, String world) { this(delegate, world, 2); }
    FrontierV3ScheduledPreparation(ScheduledActionPlanner<S> delegate, String world, int adviceWorkers) {
        if (adviceWorkers < 1 || adviceWorkers > 4) throw new IllegalArgumentException("advice workers must be 1..4");
        this.delegate = Objects.requireNonNull(delegate); this.traceWorld = Objects.requireNonNull(world);
        // Preserve the two waiting slots, including auxiliary/checkpoint headroom.
        // Parallelism changes only independent captured forecasts, never publication.
        worker = worker("PMV3-scheduled-advice", adviceWorkers, 2);
    }
    <T> java.util.Optional<java.util.concurrent.Future<T>> prepareValue(Supplier<T> value) {
        var trace = FrontierV3DelayTrace.task("AUXILIARY", traceWorld); trace.mark("QUEUED");
        var task = new FutureTask<T>(() -> {
            trace.mark("RUNNING");
            try { return value.get(); } finally { trace.mark("COMPUTED"); }
        }) {
            @Override protected void done() { if (!isCancelled()) { trace.mark("READY"); ready.run(); } }
        };
        try { worker.execute(task); return java.util.Optional.of(task); }
        catch (java.util.concurrent.RejectedExecutionException busy) {
            trace.mark("QUEUE_BUSY");
            if (worker.isShutdown()) throw busy;
            return java.util.Optional.empty();
        }
    }
    @Override public ScheduledCalculation begin(S state, ScheduledAction action, SimInstant instant) {
        var preparation = delegate.preparation(state, action, instant);
        if (preparation.isEmpty()) return delegate.begin(state, action, instant);
        return beginPreparation(preparation.orElseThrow());
    }
    @Override public ScheduledCalculation beginPreparation(ScheduledPreparation preparation) {
        var trace = FrontierV3DelayTrace.task("EXACT", traceWorld); exactTrace = trace; trace.mark("QUEUED");
        var task = new FutureTask<Supplier<List<ProposedEvent>>>(() -> {
            long began = System.nanoTime(); trace.mark("RUNNING");
            try { return preparation.prepare(); }
            finally {
                long elapsed = System.nanoTime() - began;
                totalNanos.addAndGet(elapsed); maxNanos.accumulateAndGet(elapsed, Math::max); completed.incrementAndGet();
                trace.mark("COMPUTED");
            }
        }) {
            @Override protected void done() { if (!isCancelled()) { trace.mark("READY"); ready.run(); } }
        };
        boolean accepted;
        try { exactWorker.execute(task); accepted = true; }
        catch (RejectedExecutionException busy) {
            trace.mark("QUEUE_BUSY");
            if (exactWorker.isShutdown()) throw busy;
            accepted = false;
        }
        final boolean initiallySubmitted = accepted;
        return new ScheduledCalculation() {
            private boolean submitted = initiallySubmitted;
            @Override public Optional<List<ProposedEvent>> poll() {
                if (!submitted) {
                    try { trace.mark("QUEUED"); exactWorker.execute(task); submitted = true; }
                    catch (RejectedExecutionException busy) {
                        trace.mark("QUEUE_BUSY");
                        if (exactWorker.isShutdown()) throw busy;
                        return Optional.empty();
                    }
                }
                if (!task.isDone()) return Optional.empty();
                trace.mark("OWNER_COMPLETION_BEGIN");
                try { return Optional.of(List.copyOf(task.get().get())); }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException("scheduled preparation interrupted", error);
                } catch (ExecutionException error) {
                    throw new IllegalStateException("scheduled exact preparation failed", error.getCause());
                } finally { trace.mark("OWNER_COMPLETION_END"); }
            }
            @Override public void cancel() {
                trace.mark("CANCELLED"); cancelled++;
                if (task.isDone() && !task.isCancelled()) cancelledReady++;
                task.cancel(true); exactWorker.remove(task);
            }
        };
    }
    @Override public Optional<ScheduledAdvisory<S>> advice(S state, ScheduledAction action, SimInstant instant) {
        return delegate.advice(state, action, instant);
    }
    @Override public boolean adviceAvailable() { return worker.getQueue().remainingCapacity() > 1 && !worker.isShutdown(); }
    @Override public Optional<ScheduledAdviceCalculation<S>> beginAdvice(ScheduledAdvisory<S> advice) {
        var trace = FrontierV3DelayTrace.task("ADVICE", traceWorld); trace.mark("QUEUED");
        var task = new FutureTask<ScheduledAdvisory.Completion<S>>(() -> {
            long began = System.nanoTime(); trace.mark("RUNNING");
            try { return advice.prepare(); }
            finally {
                long elapsed = System.nanoTime() - began;
                trace.mark("COMPUTED");
                adviceNanos.addAndGet(elapsed); adviceCompleted.incrementAndGet();
                totalNanos.addAndGet(elapsed); maxNanos.accumulateAndGet(elapsed, Math::max); completed.incrementAndGet();
            }
        }) { @Override protected void done() { if (!isCancelled()) { trace.mark("READY"); ready.run(); } } };
        if (worker.getQueue().remainingCapacity() <= 1) { trace.mark("QUEUE_BUSY"); return Optional.empty(); }
        try { worker.execute(task); }
        catch (RejectedExecutionException busy) {
            trace.mark("QUEUE_BUSY");
            if (worker.isShutdown()) throw busy;
            return Optional.empty();
        }
        return Optional.of(new ScheduledAdviceCalculation<>() {
            @Override public boolean ready() { return task.isDone(); }
            @Override public Optional<ScheduledAdvisory.Completion<S>> poll() {
                if (!task.isDone()) return Optional.empty();
                try {
                    trace.mark("OBSERVED");
                    var completion = task.get();
                    return Optional.of((current, at) -> {
                        trace.mark("REVALIDATION_BEGIN");
                        var result = completion.revalidate(current, at);
                        if (result.isPresent()) { trace.mark("ACCEPTED"); adviceAccepted++; }
                        else {
                            adviceRejected++;
                            String reason = completion.rejectionReason();
                            trace.mark("REJECTED", reason, 0);
                            if (reason == null || !reason.matches("[A-Z_]{1,32}")
                                    || !adviceRejectionReasons.containsKey(reason) && adviceRejectionReasons.size() >= 16)
                                reason = "OTHER";
                            adviceRejectionReasons.merge(reason, 1L, Long::sum);
                        }
                        return result;
                    });
                }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException("scheduled advice interrupted", error);
                } catch (ExecutionException error) { throw new IllegalStateException("scheduled advice failed", error.getCause()); }
            }
            @Override public void cancel() {
                trace.mark("CANCELLED"); cancelled++; if (task.isDone() && !task.isCancelled()) cancelledReady++;
                task.cancel(true); worker.remove(task);
            }
        });
    }
    @Override public List<ProposedEvent> plan(S state, ScheduledAction action) { return delegate.plan(state, action); }
    @Override public List<ProposedEvent> plan(S state, ScheduledAction action, SimInstant instant) { return delegate.plan(state, action, instant); }
    @Override public Optional<ScheduledPreparation> preparation(S state, ScheduledAction action, SimInstant instant) { return delegate.preparation(state, action, instant); }
    @Override public boolean held(S state, ScheduledAction action) { return delegate.held(state, action); }
    @Override public Optional<ScheduledHold<S>> holdProof(S state, ScheduledAction action) { return delegate.holdProof(state, action); }
    @Override public int admissionWeight(S state, ScheduledAction action) { return delegate.admissionWeight(state, action); }
    @Override public Set<SubjectId> holdWakeKeys(S state, ScheduledAction action) { return delegate.holdWakeKeys(state, action); }
    @Override public Set<SubjectId> wakeKeys(S previous, S next, FrontierEvent event) { return delegate.wakeKeys(previous, next, event); }
    @Override public List<ScheduledAction> reconsideredBy(S previous, S next, SimInstant instant) {
        return delegate.reconsideredBy(previous, next, instant);
    }
    @Override public List<ScheduledAction> retiredBy(S previous, S next, FrontierEvent event, Supplier<List<ScheduledAction>> pending) {
        return delegate.retiredBy(previous, next, event, pending);
    }
    @Override public void close() { worker.shutdownNow(); exactWorker.shutdownNow(); }
}
