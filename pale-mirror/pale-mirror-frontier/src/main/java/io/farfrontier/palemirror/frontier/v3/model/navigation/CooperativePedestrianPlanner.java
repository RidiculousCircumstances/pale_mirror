package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime-owned work queue. Queries never survey terrain; each turn advances one fair bounded slice. */
public final class CooperativePedestrianPlanner implements PedestrianRoutePlanner, AutoCloseable {
    private static final int MAX_PENDING = 8, MAX_RESULTS = 128, MAX_RETAINED_SURFACES = 131_072;
    private final Map<PedestrianRouteRequest, HierarchicalPedestrianSearch> pending = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianRouteResult> completed = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianRouteResult> deferred = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianPlanningChange.Kind> changes = new LinkedHashMap<>();
    private final ArrayDeque<PedestrianRouteRequest> ready = new ArrayDeque<>();
    private final PedestrianRegionCache regions = new PedestrianRegionCache();
    private int retainedSurfaces;
    private long workUnits;
    private boolean closed;
    private Object version;
    private long progressRevision;

    @Override public synchronized PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (version != geometry.version()) {
            pending.keySet().forEach(this::invalidated);
            completed.keySet().forEach(this::invalidated);
            deferred.keySet().forEach(this::invalidated);
            pending.clear(); ready.clear(); completed.clear(); deferred.clear(); regions.clear(); retainedSurfaces = 0;
            version = geometry.version();
            progressRevision++;
        }
        var request = PedestrianRouteRequest.of(geometry, start, target);
        PedestrianRouteResult result = completed.get(request);
        if (result != null) return result;
        if (!pending.containsKey(request)) {
            if (pending.size() >= MAX_PENDING) {
                var waiting = waiting("PLANNING_QUEUE_CAPACITY");
                if (deferred.size() >= MAX_RESULTS && !deferred.containsKey(request)) {
                    var evicted = deferred.keySet().iterator().next();
                    deferred.remove(evicted); invalidated(evicted);
                }
                deferred.put(request, waiting); return waiting;
            }
            deferred.remove(request);
            pending.put(request, new HierarchicalPedestrianSearch(geometry, start, target, regions)); ready.addLast(request);
        }
        return waiting("PLANNING_QUEUED");
    }

    public synchronized void advance(int workBudget) {
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (workBudget < HierarchicalPedestrianSearch.MIN_SLICE_WORK) throw new IllegalArgumentException("planning budget is too small");
        var request = ready.pollFirst();
        if (request == null) return;
        var search = pending.get(request);
        long previous = search.workUnits();
        PedestrianRouteResult result = search.advance(workBudget);
        workUnits += result.workUnits() - previous;
        if (result.status() == PedestrianRouteResult.Status.PLANNING) { ready.addLast(request); return; }
        pending.remove(request);
        while (!completed.isEmpty() && (completed.size() >= MAX_RESULTS
                || retainedSurfaces + result.route().size() > MAX_RETAINED_SURFACES)) {
            var oldest = completed.entrySet().iterator();
            var evicted = oldest.next();
            retainedSurfaces -= evicted.getValue().route().size(); invalidated(evicted.getKey()); oldest.remove();
        }
        completed.put(request, result); retainedSurfaces += result.route().size();
        changes.put(request, PedestrianPlanningChange.Kind.RESULT_AVAILABLE);
        // Admission resumes the exact deferred calculation, never broadcasts a free slot to all processes.
        while (pending.size() < MAX_PENDING && !deferred.isEmpty()) {
            var admitted = deferred.keySet().iterator().next(); deferred.remove(admitted);
            pending.put(admitted, new HierarchicalPedestrianSearch(admitted.geometry(), admitted.start(), admitted.target(), regions));
            ready.addLast(admitted);
        }
        progressRevision++;
    }
    /** Diagnostic counter only. Addressed changes, not this global counter, wake continuations. */
    public synchronized long progressRevision() { return progressRevision; }
    private void invalidated(PedestrianRouteRequest request) { changes.put(request, PedestrianPlanningChange.Kind.INVALIDATED); }
    public synchronized java.util.List<PedestrianPlanningChange> drainChanges() {
        var result = changes.entrySet().stream().map(entry -> new PedestrianPlanningChange(entry.getKey(), entry.getValue())).toList();
        changes.clear(); return result;
    }
    @Override public synchronized java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (version != geometry.version()) return java.util.Optional.empty();
        var request = PedestrianRouteRequest.of(geometry, start, target);
        var result = completed.get(request);
        if (result != null) return java.util.Optional.of(result);
        if (pending.containsKey(request)) return java.util.Optional.of(waiting("PLANNING_QUEUED"));
        return java.util.Optional.ofNullable(deferred.get(request));
    }
    public synchronized int pendingCount() { return pending.size(); }
    public synchronized long workUnits() { return workUnits; }
    private static PedestrianRouteResult waiting(String reason) {
        return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, java.util.List.of(), 0L, 0, reason);
    }
    @Override public synchronized void close() { closed = true; pending.clear(); ready.clear(); completed.clear(); deferred.clear(); changes.clear(); regions.clear(); retainedSurfaces = 0; }
}
