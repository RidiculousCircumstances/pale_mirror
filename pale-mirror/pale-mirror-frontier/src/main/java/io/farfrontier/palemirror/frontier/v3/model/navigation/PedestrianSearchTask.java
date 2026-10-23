package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Server-owned generation and immutable worker slice. No worker publishes a result to callers. */
final class PedestrianSearchTask {
    record Step(PedestrianRouteResult result, int spent) { }
    private final Function<PedestrianRouteRequest, HierarchicalPedestrianSearch> factory;
    private PedestrianRouteRequest request;
    private HierarchicalPedestrianSearch search;
    private PedestrianRouteGeometry searchGeometry;
    private CompletableFuture<Step> flight;
    private long work;
    private long restarts;
    private String stage = "QUEUED";
    private volatile boolean cancelled;

    PedestrianSearchTask(PedestrianRouteRequest request, Function<PedestrianRouteRequest, HierarchicalPedestrianSearch> factory) {
        this.request = request; this.factory = factory; reset();
    }
    PedestrianRouteRequest request() { return request; }
    long workUnits() { return work; }
    long restarts() { return restarts; }
    String stage() { return stage; }
    PedestrianRegionDependencies dependencies() { return search.dependencies(); }
    boolean compatible(PedestrianRouteGeometry geometry) {
        return geometry == request.geometry() || flight == null && search.compatible(geometry);
    }
    boolean rebase(PedestrianRouteRequest next) {
        if (flight != null) { request = next; return true; } // Never wait for a worker's search lock.
        if (!search.rebase(next.geometry())) return false;
        request = next; searchGeometry = next.geometry(); return true;
    }
    Step advance(int budget, BooleanSupplier mayContinue) {
        search.prepareReuse();
        Step step = calculate(search, budget, mayContinue); work += step.spent(); stage = step.result().reason(); search.publishReuse(); return step;
    }
    void submit(Executor executor, int budget) {
        if (flight != null || cancelled) throw new IllegalStateException("pedestrian slice already running or retired");
        search.prepareReuse();
        var result = new CompletableFuture<Step>(); flight = result;
        var captured = search;
        try {
            executor.execute(() -> {
                try { result.complete(calculate(captured, budget, () -> !cancelled)); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RuntimeException rejected) { flight = null; throw rejected; }
    }
    /** Nonblocking. New geometry is checked before publication, not in the worker. */
    Step poll() {
        if (flight == null || !flight.isDone()) return null;
        Step step = flight.join(); flight = null; work += step.spent();
        stage = step.result().reason();
        if (cancelled) return step;
        if (searchGeometry != request.geometry()) {
            if (!search.rebase(request.geometry())) {
                restarts++; stage = "CHANGED_WORKER_DEPENDENCY"; reset();
                return new Step(new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING,
                        java.util.List.of(), 0, 0, "CHANGED_WORKER_DEPENDENCY"), step.spent());
            }
            searchGeometry = request.geometry();
        }
        search.publishReuse(); return step;
    }
    void cancel() { cancelled = true; }
    private void reset() { search = factory.apply(request); searchGeometry = request.geometry(); }
    private static Step calculate(HierarchicalPedestrianSearch search, int budget, BooleanSupplier mayContinue) {
        long before = search.workUnits(); var result = search.advance(budget, mayContinue);
        search.preparePublication();
        int spent = Math.toIntExact(result.workUnits() - before);
        if (spent < 0 || spent > budget) throw new IllegalStateException("pedestrian search exceeded its slice: " + spent + "/" + budget);
        return new Step(result, spent);
    }
}
