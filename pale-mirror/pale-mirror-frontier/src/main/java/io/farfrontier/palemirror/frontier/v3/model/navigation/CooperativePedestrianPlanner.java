package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime-owned work queue. Queries never survey terrain; each turn spends one shared bounded budget. */
public final class CooperativePedestrianPlanner implements PedestrianRoutePlanner, AutoCloseable {
    private static final int MAX_PENDING = 8, MAX_RESULTS = 128, MAX_RETAINED_SURFACES = 131_072, MAX_RETAINED_REGIONS = 16_384;
    private record Completed(PedestrianRouteResult result, PedestrianRegionDependencies dependencies) { }
    private final Map<PedestrianRouteRequest, PedestrianSearchTask> pending = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianSearchTask> suspended = new LinkedHashMap<>();
    private final PedestrianPlanningFairness fairness = new PedestrianPlanningFairness();
    private final java.util.concurrent.Executor executor;
    private final ArrayDeque<PedestrianSearchTask> inFlight = new ArrayDeque<>();
    private final Map<PedestrianRouteRequest, Completed> completed = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianRouteResult> deferred = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianPlanningChange.Kind> changes = new LinkedHashMap<>();
    private final ArrayDeque<PedestrianRouteRequest> ready = new ArrayDeque<>();
    private final PedestrianRegionCache regions = new PedestrianRegionCache();
    private final PedestrianCorridorCache corridors = new PedestrianCorridorCache();
    private final PedestrianItineraryCache itineraries = new PedestrianItineraryCache();
    private int retainedSurfaces;
    private int retainedRegions;
    private long workUnits;
    private boolean closed;
    private Object version;
    private long progressRevision;
    private long turns;
    private long completedCount;
    private final Map<PedestrianRouteRequest, Long> admittedAt = new LinkedHashMap<>();
    private final Map<Object, PedestrianRouteGeometry> synchronizedScopes = new LinkedHashMap<>();

    public CooperativePedestrianPlanner() { executor = null; }
    /** The adapter owns a bounded executor. This planner owns deterministic admission/publication. */
    public CooperativePedestrianPlanner(java.util.concurrent.Executor executor) {
        this.executor = java.util.Objects.requireNonNull(executor);
    }
    private PedestrianSearchTask task(PedestrianRouteRequest request) {
        return new PedestrianSearchTask(request, value -> new HierarchicalPedestrianSearch(value.geometry(), value.start(),
                value.target(), regions, corridors, value.extent(), itineraries));
    }

    @Override public synchronized PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return query(geometry, start, target, PedestrianRouteExtent.COMPLETE);
    }
    @Override public synchronized PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                                               PedestrianRouteExtent extent) {
        return query(geometry, start, target, extent, PedestrianPlanningService.ORDINARY);
    }
    @Override public synchronized PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                                               PedestrianRouteExtent extent, PedestrianPlanningService service) {
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (geometry.regionalRevision().isPresent()) synchronizeScope(geometry);
        else if (version != geometry.version()) {
            for (var request : java.util.List.copyOf(pending.keySet())) if (request.geometry().regionalRevision().isEmpty()) {
                var removed = pending.remove(request); removed.cancel(); ready.remove(request); invalidated(request);
            }
            for (var request : java.util.List.copyOf(completed.keySet())) if (request.geometry().regionalRevision().isEmpty()) {
                removeCompleted(request); invalidated(request);
            }
            for (var request : java.util.List.copyOf(deferred.keySet())) if (request.geometry().regionalRevision().isEmpty()) {
                deferred.remove(request); discardSuspended(request); invalidated(request);
            }
            version = geometry.version();
            progressRevision++;
        }
        var request = PedestrianRouteRequest.of(geometry, start, target, extent);
        Completed result = completed.get(request);
        if (result != null) return result.result();
        fairness.requested(request, service);
        admittedAt.putIfAbsent(request, turns);
        if (!pending.containsKey(request)) {
            if (deferred.size() >= MAX_RESULTS && !deferred.containsKey(request)) {
                var evicted = deferred.keySet().iterator().next();
                deferred.remove(evicted); discardSuspended(evicted); invalidated(evicted);
            }
            deferred.putIfAbsent(request, waiting("PLANNING_QUEUE_CAPACITY"));
        }
        admitDeferred();
        return waiting(pending.containsKey(request) ? "PLANNING_QUEUED" : "PLANNING_QUEUE_CAPACITY");
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
        admitDeferred();
        if (executor != null) { advanceWorkers(workBudget, mayContinue); return; }
        int remaining = workBudget;
        // A bounded number of visits per turn also bounds zero-cost terminals.
        // A long search rotates behind its peers; a short completion gives its
        // unused budget to the next peer, including a newly admitted deferred one.
        int visits = pending.size() + deferred.size();
        while (remaining >= HierarchicalPedestrianSearch.MIN_SLICE_WORK && visits-- > 0 && !ready.isEmpty()
                && mayContinue.getAsBoolean()) {
            var request = fairness.take(ready);
            var search = pending.get(request);
            int slice = Math.min(remaining, HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            var step = search.advance(slice, mayContinue);
            PedestrianRouteResult result = step.result(); int spent = step.spent();
            workUnits += spent; remaining -= spent;
            if (spent > 0) progressRevision++;
            if (result.status() == PedestrianRouteResult.Status.PLANNING) { ready.addLast(request); continue; }
            complete(request, search, result);
        }
    }
    private void advanceWorkers(int budget, java.util.function.BooleanSupplier mayContinue) {
        // Independent lanes do not fence each other. Within each lane retain
        // submission order; publication is server-owned, never a worker callback.
        int publications = MAX_PENDING;
        var blocked = java.util.EnumSet.noneOf(PedestrianPlanningFairness.Lane.class);
        for (var task : java.util.List.copyOf(inFlight)) {
            if (publications-- == 0 || !mayContinue.getAsBoolean()) break;
            var request = task.request();
            if (pending.get(request) != task) { inFlight.remove(task); continue; }
            var lane = fairness.lane(request);
            if (blocked.contains(lane)) continue;
            var step = task.poll();
            if (step == null) { blocked.add(lane); continue; }
            inFlight.remove(task); workUnits += step.spent();
            if (step.spent() > 0) progressRevision++;
            if (step.result().status() == PedestrianRouteResult.Status.PLANNING) ready.addLast(request);
            else complete(request, task, step.result());
        }
        admitDeferred();
        int slice = HierarchicalPedestrianSearch.MIN_SLICE_WORK;
        while (budget >= slice && !ready.isEmpty() && inFlight.size() < MAX_PENDING && mayContinue.getAsBoolean()) {
            var request = fairness.take(ready); var task = pending.get(request);
            task.submit(executor, slice); inFlight.addLast(task); budget -= slice;
        }
    }
    private void complete(PedestrianRouteRequest request, PedestrianSearchTask search, PedestrianRouteResult result) {
        pending.remove(request);
        admittedAt.remove(request); fairness.remove(request); completedCount++;
        var dependency = search.dependencies();
        corridors.remember(request.geometry(), result, dependency);
        while (!completed.isEmpty() && (completed.size() >= MAX_RESULTS
                || retainedSurfaces + result.route().size() > MAX_RETAINED_SURFACES
                || retainedRegions + dependency.revisions().size() > MAX_RETAINED_REGIONS)) {
            var oldest = completed.keySet().iterator().next(); removeCompleted(oldest); invalidated(oldest);
        }
        completed.put(request, new Completed(result, dependency)); retainedSurfaces += result.route().size();
        retainedRegions += dependency.revisions().size();
        changes.put(request, PedestrianPlanningChange.Kind.RESULT_AVAILABLE);
        admitDeferred();
        progressRevision++;
    }
    private void admitDeferred() {
        // Admission resumes the exact deferred calculation, never broadcasts a free slot to all processes.
        // Also used after invalidation frees capacity, before a new caller can overtake retained waiters.
        int visits = MAX_PENDING;
        while (!deferred.isEmpty() && visits-- > 0) {
            var admitted = fairness.nextAdmission(deferred.keySet(), pending.keySet());
            if (pending.size() == MAX_PENDING) {
                var victim = fairness.yieldFor(admitted, pending.keySet(), ready);
                if (victim == null) break;
                // Saved searches own large bounded graphs. Keep at most another
                // active-window worth; evicting derived work grants no outcome.
                if (suspended.size() == MAX_PENDING) discardSuspended(suspended.keySet().iterator().next());
                suspended.put(victim, pending.remove(victim)); ready.remove(victim);
                deferred.put(victim, waiting("PLANNING_SERVICE_YIELD"));
            }
            deferred.remove(admitted);
            var retained = suspended.remove(admitted);
            pending.put(admitted, retained == null ? task(admitted) : retained);
            ready.addLast(admitted);
            progressRevision++;
        }
    }
    private void discardSuspended(PedestrianRouteRequest request) {
        var retained = suspended.remove(request); if (retained != null) retained.cancel();
    }
    @Override public synchronized void discardUnobserved(PedestrianRouteRequest request) {
        var search = pending.remove(request);
        if (search != null) { search.cancel(); inFlight.remove(search); ready.remove(request); }
        deferred.remove(request); discardSuspended(request);
        if (admittedAt.containsKey(request)) invalidated(request);
    }
    @Override public synchronized boolean hasCalculation(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                                         PedestrianRouteExtent extent) {
        return admittedAt.keySet().stream().anyMatch(request -> request.start().equals(start) && request.target().equals(target)
                && request.extent().equals(extent) && (request.geometry() == geometry
                    || geometry.regionalRevision().isPresent() && sameScope(request, geometry)));
    }
    /** Diagnostic counter only. Addressed changes, not this global counter, wake continuations. */
    public synchronized long progressRevision() { return progressRevision; }
    private void invalidated(PedestrianRouteRequest request) { admittedAt.remove(request); fairness.remove(request); changes.put(request, PedestrianPlanningChange.Kind.INVALIDATED); }
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
        Object scope = geometry.regionalRevision().orElseThrow().scope();
        if (synchronizedScopes.get(scope) == geometry) return;
        for (var request : java.util.List.copyOf(pending.keySet())) if (request.geometry() != geometry && sameScope(request, geometry)) {
            long since = admittedAt.get(request);
            var replacement = PedestrianRouteRequest.of(geometry, request.start(), request.target(), request.extent());
            var lane = fairness.lane(request);
            var search = pending.remove(request); invalidated(request);
            if (search.rebase(replacement)) {
                fairness.restore(replacement, lane);
                pending.put(replacement, search);
                // Replacing an immutable view is not a new calculation turn.
                // Preserve round-robin position: rebuilding views every tick
                // must not reset rotation and starve all but its first search.
                var rotation = new ArrayDeque<PedestrianRouteRequest>();
                for (var queued : ready) rotation.addLast(queued.equals(request) ? replacement : queued);
                ready.clear(); ready.addAll(rotation);
                admittedAt.put(replacement, since);
            } else { fairness.remove(replacement); search.cancel(); inFlight.remove(search); ready.remove(request); }
            progressRevision++;
        }
        for (var request : java.util.List.copyOf(completed.keySet())) if (request.geometry() != geometry && sameScope(request, geometry)) {
            var result = completed.get(request); removeCompleted(request); invalidated(request);
            if (result.dependencies().matches(geometry)) {
                var replacement = PedestrianRouteRequest.of(geometry, request.start(), request.target(), request.extent());
                completed.put(replacement, result); retainedSurfaces += result.result().route().size();
                retainedRegions += result.dependencies().revisions().size();
                changes.put(replacement, PedestrianPlanningChange.Kind.RESULT_AVAILABLE);
            }
            progressRevision++;
        }
        var rebasedDeferred = new LinkedHashMap<PedestrianRouteRequest, PedestrianRouteResult>();
        for (var entry : deferred.entrySet()) {
            var request = entry.getKey();
            if (request.geometry() == geometry || !sameScope(request, geometry)) {
                rebasedDeferred.put(request, entry.getValue()); continue;
            }
            long since = admittedAt.get(request);
            var replacement = PedestrianRouteRequest.of(geometry, request.start(), request.target(), request.extent());
            var lane = fairness.lane(request); invalidated(request); fairness.restore(replacement, lane);
            var retained = suspended.remove(request);
            if (retained != null) {
                if (retained.rebase(replacement)) suspended.put(replacement, retained);
                else retained.cancel();
            }
            rebasedDeferred.put(replacement, entry.getValue()); admittedAt.put(replacement, since); progressRevision++;
        }
        // A refreshed view is the same demand, not a new arrival at the end of the queue.
        deferred.clear(); deferred.putAll(rebasedDeferred);
        if (synchronizedScopes.size() >= MAX_RESULTS && !synchronizedScopes.containsKey(scope))
            synchronizedScopes.remove(synchronizedScopes.keySet().iterator().next());
        synchronizedScopes.put(scope, geometry);
    }
    public synchronized java.util.List<PedestrianPlanningChange> drainChanges() {
        var result = changes.entrySet().stream().map(entry -> new PedestrianPlanningChange(entry.getKey(), entry.getValue())).toList();
        changes.clear(); return result;
    }
    @Override public synchronized java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return peek(geometry, start, target, PedestrianRouteExtent.COMPLETE);
    }
    @Override public synchronized java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                                                                PedestrianRouteExtent extent) {
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        if (geometry.regionalRevision().isEmpty() && version != geometry.version()) return java.util.Optional.empty();
        var request = PedestrianRouteRequest.of(geometry, start, target, extent);
        var result = completed.get(request);
        if (result != null) return java.util.Optional.of(result.result());
        if (pending.containsKey(request)) return java.util.Optional.of(waiting("PLANNING_QUEUED"));
        var waiting = deferred.get(request);
        if (waiting != null) return java.util.Optional.of(waiting);
        if (geometry.regionalRevision().isPresent()) {
            for (var entry : completed.entrySet()) if (sameScope(entry.getKey(), geometry)
                    && entry.getKey().extent().equals(extent)
                    && entry.getKey().start().equals(start) && entry.getKey().target().equals(target)
                    && entry.getValue().dependencies().matches(geometry)) return java.util.Optional.of(entry.getValue().result());
            for (var entry : pending.entrySet()) if (sameScope(entry.getKey(), geometry)
                    && entry.getKey().extent().equals(extent)
                    && entry.getKey().start().equals(start) && entry.getKey().target().equals(target)
                    && entry.getValue().compatible(geometry)) return java.util.Optional.of(waiting("PLANNING_QUEUED"));
        }
        return java.util.Optional.empty();
    }
    public synchronized int pendingCount() { return pending.size(); }
    public synchronized int inFlightCount() { return inFlight.size(); }
    public synchronized int deferredCount() { return deferred.size(); }
    public record ServiceDiagnostic(String service, long active, long waiting, long oldestWaitTurns) { }
    public synchronized java.util.List<ServiceDiagnostic> serviceDiagnostics() {
        return java.util.Arrays.stream(PedestrianPlanningFairness.Lane.values()).map(lane -> new ServiceDiagnostic(lane.name(),
                pending.keySet().stream().filter(request -> fairness.lane(request) == lane).count(),
                deferred.keySet().stream().filter(request -> fairness.lane(request) == lane).count(),
                admittedAt.entrySet().stream().filter(entry -> fairness.lane(entry.getKey()) == lane)
                        .mapToLong(entry -> turns - entry.getValue()).max().orElse(0L))).toList();
    }
    public synchronized int suspendedCount() { return suspended.size(); }
    /** Host planning turns, not canonical ticks or a product timeout. */
    public synchronized long oldestWaitTurns() {
        return admittedAt.values().stream().mapToLong(since -> turns - since).max().orElse(0L);
    }
    public record WaitingDiagnostic(SurfaceAnchor start, SurfaceAnchor target, boolean deferred,
                                    long waitTurns, long searchWorkUnits, PedestrianRouteExtent extent) { }
    /** Read-only bounded oldest-first view. It cannot advance, rebase, admit or wake anything. */
    public synchronized java.util.List<WaitingDiagnostic> waitingDiagnostics(int maximum) {
        if (maximum < 0 || maximum > MAX_PENDING + MAX_RESULTS)
            throw new IllegalArgumentException("waiting diagnostic limit exceeds the bounded queue");
        return admittedAt.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(maximum).map(entry -> {
            var request = entry.getKey(); var search = pending.get(request);
            return new WaitingDiagnostic(request.start(), request.target(), search == null,
                    turns - entry.getValue(), search == null ? 0L : search.workUnits(), request.extent());
        }).toList();
    }
    public synchronized long completedCount() { return completedCount; }
    public synchronized long workUnits() { return workUnits; }
    public synchronized long corridorCandidates() { return corridors.hits(); }
    private static PedestrianRouteResult waiting(String reason) {
        return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, java.util.List.of(), 0L, 0, reason);
    }
    @Override public synchronized void close() {
        closed = true; pending.values().forEach(PedestrianSearchTask::cancel);
        suspended.values().forEach(PedestrianSearchTask::cancel); suspended.clear(); fairness.clear();
        pending.clear(); inFlight.clear(); ready.clear(); completed.clear(); deferred.clear(); admittedAt.clear(); changes.clear();
        regions.clear(); corridors.clear(); itineraries.clear(); retainedSurfaces = 0; retainedRegions = 0;
        synchronizedScopes.clear();
    }
}
