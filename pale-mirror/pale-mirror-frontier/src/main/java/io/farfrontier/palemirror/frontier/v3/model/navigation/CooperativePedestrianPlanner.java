package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Runtime-owned work queue. Queries never survey terrain; each turn advances one fair bounded slice. */
public final class CooperativePedestrianPlanner implements PedestrianRoutePlanner, AutoCloseable {
    private static final int MAX_PENDING = 8, MAX_RESULTS = 128, MAX_RETAINED_SURFACES = 131_072;
    private record Request(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) { }
    private final Map<Request, HierarchicalPedestrianSearch> pending = new LinkedHashMap<>();
    private final Map<Request, PedestrianRouteResult> completed = new LinkedHashMap<>();
    private final ArrayDeque<Request> ready = new ArrayDeque<>();
    private final PedestrianRegionCache regions = new PedestrianRegionCache();
    private int retainedSurfaces;
    private long workUnits;
    private boolean closed;
    private Object version;

    @Override public synchronized PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (version != geometry.version()) {
            pending.clear(); ready.clear(); completed.clear(); regions.clear(); retainedSurfaces = 0;
            version = geometry.version();
        }
        Request request = new Request(Objects.requireNonNull(geometry), Objects.requireNonNull(start), Objects.requireNonNull(target));
        PedestrianRouteResult result = completed.get(request);
        if (result != null) return result;
        if (!pending.containsKey(request)) {
            if (pending.size() >= MAX_PENDING) return waiting("PLANNING_QUEUE_CAPACITY");
            pending.put(request, new HierarchicalPedestrianSearch(geometry, start, target, regions)); ready.addLast(request);
        }
        return waiting("PLANNING_QUEUED");
    }

    public synchronized void advance(int workBudget) {
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (workBudget < HierarchicalPedestrianSearch.MIN_SLICE_WORK) throw new IllegalArgumentException("planning budget is too small");
        Request request = ready.pollFirst();
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
            retainedSurfaces -= oldest.next().getValue().route().size(); oldest.remove();
        }
        completed.put(request, result); retainedSurfaces += result.route().size();
    }
    public synchronized int pendingCount() { return pending.size(); }
    public synchronized long workUnits() { return workUnits; }
    private static PedestrianRouteResult waiting(String reason) {
        return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, java.util.List.of(), 0L, 0, reason);
    }
    @Override public synchronized void close() { closed = true; pending.clear(); ready.clear(); completed.clear(); regions.clear(); retainedSurfaces = 0; }
}
