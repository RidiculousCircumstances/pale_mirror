package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime-owned work queue. Queries never survey terrain; each turn spends one shared bounded budget. */
public final class CooperativePedestrianPlanner implements PedestrianRoutePlanner, AutoCloseable {
    private static final int MAX_PENDING = 8, MAX_RESULTS = 128, MAX_RETAINED_SURFACES = 131_072, MAX_RETAINED_REGIONS = 16_384;
    private record Completed(PedestrianRouteResult result, PedestrianRegionDependencies dependencies) { }
    private final Map<PedestrianRouteRequest, HierarchicalPedestrianSearch> pending = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, Completed> completed = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianRouteResult> deferred = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianPlanningChange.Kind> changes = new LinkedHashMap<>();
    private final ArrayDeque<PedestrianRouteRequest> ready = new ArrayDeque<>();
    private final PedestrianRegionCache regions = new PedestrianRegionCache();
    private int retainedSurfaces;
    private int retainedRegions;
    private long workUnits;
    private boolean closed;
    private Object version;
    private long progressRevision;
    private long turns;
    private long completedCount;
    private final Map<PedestrianRouteRequest, Long> admittedAt = new LinkedHashMap<>();

    @Override public synchronized PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (geometry.regionalRevision().isPresent()) synchronizeScope(geometry);
        else if (version != geometry.version()) {
            for (var request : java.util.List.copyOf(pending.keySet())) if (request.geometry().regionalRevision().isEmpty()) {
                pending.remove(request); ready.remove(request); invalidated(request);
            }
            for (var request : java.util.List.copyOf(completed.keySet())) if (request.geometry().regionalRevision().isEmpty()) {
                removeCompleted(request); invalidated(request);
            }
            for (var request : java.util.List.copyOf(deferred.keySet())) if (request.geometry().regionalRevision().isEmpty()) {
                deferred.remove(request); invalidated(request);
            }
            version = geometry.version();
            progressRevision++;
        }
        var request = PedestrianRouteRequest.of(geometry, start, target);
        Completed result = completed.get(request);
        if (result != null) return result.result();
        admittedAt.putIfAbsent(request, turns);
        if (!pending.containsKey(request)) {
            if (!canAdmit(request)) {
                var waiting = waiting("PLANNING_QUEUE_CAPACITY");
                if (deferred.size() >= MAX_RESULTS && !deferred.containsKey(request)) {
                    var evicted = deferred.keySet().iterator().next();
                    deferred.remove(evicted); admittedAt.remove(evicted); invalidated(evicted);
                }
                deferred.put(request, waiting); return waiting;
            }
            deferred.remove(request);
            pending.put(request, new HierarchicalPedestrianSearch(geometry, start, target, regions)); ready.addLast(request);
        }
        return waiting("PLANNING_QUEUED");
    }

    public synchronized void advance(int workBudget) {
        advance(workBudget, () -> true);
    }
    /** Optional host admission fence; fair queue and exact searches survive yielding. */
    public synchronized void advance(int workBudget, java.util.function.BooleanSupplier mayContinue) {
        java.util.Objects.requireNonNull(mayContinue);
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (workBudget < HierarchicalPedestrianSearch.MIN_SLICE_WORK) throw new IllegalArgumentException("planning budget is too small");
        turns++;
        int remaining = workBudget;
        // A bounded number of visits per turn also bounds zero-cost terminals.
        // A long search rotates behind its peers; a short completion gives its
        // unused budget to the next peer, including a newly admitted deferred one.
        int visits = pending.size() + deferred.size();
        while (remaining >= HierarchicalPedestrianSearch.MIN_SLICE_WORK && visits-- > 0 && !ready.isEmpty()
                && mayContinue.getAsBoolean()) {
            var request = ready.removeFirst();
            var search = pending.get(request);
            long previous = search.workUnits();
            int slice = Math.min(remaining, HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            PedestrianRouteResult result = search.advance(slice, mayContinue);
            int spent = Math.toIntExact(result.workUnits() - previous);
            if (spent < 0 || spent > slice) throw new IllegalStateException("pedestrian search exceeded its shared work budget"
                    + ": spent=" + spent + ", slice=" + slice + ", remaining=" + remaining
                    + ", start=" + request.start() + ", target=" + request.target());
            workUnits += spent; remaining -= spent;
            if (spent > 0) progressRevision++;
            if (result.status() == PedestrianRouteResult.Status.PLANNING) { ready.addLast(request); continue; }
            complete(request, search, result);
        }
    }
    private void complete(PedestrianRouteRequest request, HierarchicalPedestrianSearch search, PedestrianRouteResult result) {
        pending.remove(request);
        admittedAt.remove(request); completedCount++;
        var dependency = search.dependencies();
        while (!completed.isEmpty() && (completed.size() >= MAX_RESULTS
                || retainedSurfaces + result.route().size() > MAX_RETAINED_SURFACES
                || retainedRegions + dependency.revisions().size() > MAX_RETAINED_REGIONS)) {
            var oldest = completed.keySet().iterator().next(); removeCompleted(oldest); invalidated(oldest);
        }
        completed.put(request, new Completed(result, dependency)); retainedSurfaces += result.route().size();
        retainedRegions += dependency.revisions().size();
        changes.put(request, PedestrianPlanningChange.Kind.RESULT_AVAILABLE);
        // Admission resumes the exact deferred calculation, never broadcasts a free slot to all processes.
        while (pending.size() < MAX_PENDING && !deferred.isEmpty()) {
            var candidate = deferred.keySet().stream().filter(this::canAdmit).findFirst();
            if (candidate.isEmpty()) break;
            var admitted = candidate.orElseThrow(); deferred.remove(admitted);
            pending.put(admitted, new HierarchicalPedestrianSearch(admitted.geometry(), admitted.start(), admitted.target(), regions));
            ready.addLast(admitted);
        }
        progressRevision++;
    }
    /** Long inter-region queries cannot occupy the last slot needed by local service/work goals. */
    private boolean canAdmit(PedestrianRouteRequest request) {
        return pending.size() < MAX_PENDING && (PedestrianLocalSearch.nearby(request.start(), request.target())
                || pending.keySet().stream().filter(value -> !PedestrianLocalSearch.nearby(value.start(), value.target())).count() < MAX_PENDING - 1);
    }
    /** Diagnostic counter only. Addressed changes, not this global counter, wake continuations. */
    public synchronized long progressRevision() { return progressRevision; }
    private void invalidated(PedestrianRouteRequest request) { admittedAt.remove(request); changes.put(request, PedestrianPlanningChange.Kind.INVALIDATED); }
    private void removeCompleted(PedestrianRouteRequest request) {
        var removed = completed.remove(request);
        retainedSurfaces -= removed.result().route().size(); retainedRegions -= removed.dependencies().revisions().size();
    }
    private static boolean sameScope(PedestrianRouteRequest request, PedestrianRouteGeometry geometry) {
        return request.geometry().regionalRevision().isPresent()
                && request.geometry().regionalRevision().orElseThrow().scope().equals(geometry.regionalRevision().orElseThrow().scope());
    }
    /** No surveying/search here: compare at most the bounded retained regional dependency tokens. */
    private void synchronizeScope(PedestrianRouteGeometry geometry) {
        for (var request : java.util.List.copyOf(pending.keySet())) if (request.geometry() != geometry && sameScope(request, geometry)) {
            long since = admittedAt.get(request);
            var search = pending.remove(request); invalidated(request);
            if (search.rebase(geometry)) {
                var replacement = PedestrianRouteRequest.of(geometry, request.start(), request.target());
                pending.put(replacement, search);
                // Replacing an immutable view is not a new calculation turn.
                // Preserve round-robin position: rebuilding views every tick
                // must not reset rotation and starve all but its first search.
                var rotation = new ArrayDeque<PedestrianRouteRequest>();
                for (var queued : ready) rotation.addLast(queued.equals(request) ? replacement : queued);
                ready.clear(); ready.addAll(rotation);
                admittedAt.put(replacement, since);
            } else ready.remove(request);
            progressRevision++;
        }
        for (var request : java.util.List.copyOf(completed.keySet())) if (request.geometry() != geometry && sameScope(request, geometry)) {
            var result = completed.get(request); removeCompleted(request); invalidated(request);
            if (result.dependencies().matches(geometry)) {
                var replacement = PedestrianRouteRequest.of(geometry, request.start(), request.target());
                completed.put(replacement, result); retainedSurfaces += result.result().route().size();
                retainedRegions += result.dependencies().revisions().size();
                changes.put(replacement, PedestrianPlanningChange.Kind.RESULT_AVAILABLE);
            }
            progressRevision++;
        }
        for (var request : java.util.List.copyOf(deferred.keySet())) if (request.geometry() != geometry && sameScope(request, geometry)) {
            long since = admittedAt.get(request);
            var waiting = deferred.remove(request); invalidated(request);
            var replacement = PedestrianRouteRequest.of(geometry, request.start(), request.target());
            deferred.put(replacement, waiting); admittedAt.put(replacement, since); progressRevision++;
        }
    }
    public synchronized java.util.List<PedestrianPlanningChange> drainChanges() {
        var result = changes.entrySet().stream().map(entry -> new PedestrianPlanningChange(entry.getKey(), entry.getValue())).toList();
        changes.clear(); return result;
    }
    @Override public synchronized java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (geometry.regionalRevision().isEmpty() && version != geometry.version()) return java.util.Optional.empty();
        var request = PedestrianRouteRequest.of(geometry, start, target);
        var result = completed.get(request);
        if (result != null) return java.util.Optional.of(result.result());
        if (pending.containsKey(request)) return java.util.Optional.of(waiting("PLANNING_QUEUED"));
        var waiting = deferred.get(request);
        if (waiting != null) return java.util.Optional.of(waiting);
        if (geometry.regionalRevision().isPresent()) {
            for (var entry : completed.entrySet()) if (sameScope(entry.getKey(), geometry)
                    && entry.getKey().start().equals(start) && entry.getKey().target().equals(target)
                    && entry.getValue().dependencies().matches(geometry)) return java.util.Optional.of(entry.getValue().result());
            for (var entry : pending.entrySet()) if (sameScope(entry.getKey(), geometry)
                    && entry.getKey().start().equals(start) && entry.getKey().target().equals(target)
                    && entry.getValue().compatible(geometry)) return java.util.Optional.of(waiting("PLANNING_QUEUED"));
        }
        return java.util.Optional.empty();
    }
    public synchronized int pendingCount() { return pending.size(); }
    public synchronized int deferredCount() { return deferred.size(); }
    /** Host planning turns, not canonical ticks or a product timeout. */
    public synchronized long oldestWaitTurns() {
        return admittedAt.values().stream().mapToLong(since -> turns - since).max().orElse(0L);
    }
    public record WaitingDiagnostic(SurfaceAnchor start, SurfaceAnchor target, boolean deferred,
                                    long waitTurns, long searchWorkUnits) { }
    /** Read-only bounded oldest-first view. It cannot advance, rebase, admit or wake anything. */
    public synchronized java.util.List<WaitingDiagnostic> waitingDiagnostics(int maximum) {
        if (maximum < 0 || maximum > MAX_PENDING + MAX_RESULTS)
            throw new IllegalArgumentException("waiting diagnostic limit exceeds the bounded queue");
        return admittedAt.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(maximum).map(entry -> {
            var request = entry.getKey(); var search = pending.get(request);
            return new WaitingDiagnostic(request.start(), request.target(), search == null,
                    turns - entry.getValue(), search == null ? 0L : search.workUnits());
        }).toList();
    }
    public synchronized long completedCount() { return completedCount; }
    public synchronized long workUnits() { return workUnits; }
    private static PedestrianRouteResult waiting(String reason) {
        return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, java.util.List.of(), 0L, 0, reason);
    }
    @Override public synchronized void close() { closed = true; pending.clear(); ready.clear(); completed.clear(); deferred.clear(); admittedAt.clear(); changes.clear(); regions.clear(); retainedSurfaces = 0; retainedRegions = 0; }
}
