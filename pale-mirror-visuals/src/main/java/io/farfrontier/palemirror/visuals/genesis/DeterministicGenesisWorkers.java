package io.farfrontier.palemirror.visuals.genesis;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

/** Bounded indexed work pool whose output order never depends on completion order. */
public final class DeterministicGenesisWorkers implements AutoCloseable {
    private static final ThreadLocal<DeterministicGenesisWorkers> CURRENT = new ThreadLocal<>();
    private final int parallelism;
    private final ExecutorService executor;

    public DeterministicGenesisWorkers(int parallelism) {
        if (parallelism < 1 || parallelism > 32) {
            throw new IllegalArgumentException("genesis worker count must be between 1 and 32");
        }
        this.parallelism = parallelism;
        AtomicInteger serial = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(parallelism, runnable -> {
            int workerIndex = serial.getAndIncrement();
            Thread thread = new Thread(() -> {
                CURRENT.set(this);
                try {
                    runnable.run();
                } finally {
                    CURRENT.remove();
                }
            }, "PaleMirror-Genesis-Worker-" + (workerIndex + 1));
            thread.setDaemon(true);
            return thread;
        });
    }

    public int parallelism() { return parallelism; }

    /**
     * Evaluates every index at most once and returns results in ascending index order.
     * Nested calls from a worker execute inline, preventing pool starvation.
     */
    public <T> List<T> mapIndexed(int size, IntFunction<? extends T> mapper) {
        if (size < 0) throw new IllegalArgumentException("indexed work size must be non-negative");
        if (size == 0) return List.of();
        if (parallelism == 1 || CURRENT.get() == this) return sequential(size, mapper);

        Object[] results = new Object[size];
        AtomicInteger cursor = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        int taskCount = Math.min(parallelism, size);
        List<java.util.concurrent.Callable<Void>> tasks = new ArrayList<>(taskCount);
        for (int task = 0; task < taskCount; task++) tasks.add(() -> {
            for (int index = cursor.getAndIncrement(); index < size && failure.get() == null;
                 index = cursor.getAndIncrement()) {
                try {
                    results[index] = mapper.apply(index);
                } catch (Throwable thrown) {
                    failure.compareAndSet(null, thrown);
                }
            }
            return null;
        });

        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while surveying authored-region terrain", interrupted);
        } catch (ExecutionException impossible) {
            throw propagate(impossible.getCause());
        }
        Throwable thrown = failure.get();
        if (thrown != null) throw propagate(thrown);

        List<T> ordered = new ArrayList<>(size);
        for (Object result : results) {
            @SuppressWarnings("unchecked") T value = (T) result;
            ordered.add(value);
        }
        return List.copyOf(ordered);
    }

    private static <T> List<T> sequential(int size, IntFunction<? extends T> mapper) {
        List<T> results = new ArrayList<>(size);
        for (int index = 0; index < size; index++) results.add(mapper.apply(index));
        return List.copyOf(results);
    }

    private static RuntimeException propagate(Throwable thrown) {
        if (thrown instanceof RuntimeException runtime) return runtime;
        if (thrown instanceof Error error) throw error;
        return new IllegalStateException("Authored-region worker failed", thrown);
    }

    @Override public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Authored-region workers did not terminate");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping authored-region workers", interrupted);
        }
    }
}
