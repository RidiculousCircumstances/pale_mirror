package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime-owned work queue. Queries never survey terrain; each turn spends one shared bounded budget. */
public final class CooperativePedestrianPlanner implements PedestrianRoutePlanner, AutoCloseable {
    private static final int MAX_DEFERRED_DEMANDS = 65_535;
    private static final int MAX_PENDING = 8, MAX_RESULTS = 128, MAX_RETAINED_SURFACES = 131_072, MAX_RETAINED_REGIONS = 16_384;
    private record Completed(PedestrianRouteResult result, PedestrianRegionDependencies dependencies) { }
    private final Map<PedestrianRouteRequest, PedestrianSearchTask> pending = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianSearchTask> suspended = new LinkedHashMap<>();
    private final PedestrianPlanningFairness fairness = new PedestrianPlanningFairness();
    private final java.util.concurrent.Executor executor;
    private final ArrayDeque<PedestrianSearchTask> inFlight = new ArrayDeque<>();
    private record Reservation(PedestrianPlanningAllowance turn, int work) { }
    private final Map<PedestrianSearchTask, Reservation> reservations = new java.util.IdentityHashMap<>();
    private volatile Runnable readiness = () -> { };
    /** Listener queues the owner; it must never execute world/planner work inline. */
    public void onReady(Runnable listener) { readiness = java.util.Objects.requireNonNull(listener); }
    private void signalReady() { readiness.run(); }
    private final Map<PedestrianRouteRequest, Completed> completed = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianRouteResult> deferred = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, PedestrianPlanningChange.Kind> changes = new LinkedHashMap<>();
    private final ArrayDeque<PedestrianRouteRequest> ready = new ArrayDeque<>();
    private final PedestrianRegionCache regions = new PedestrianRegionCache();
    private final PedestrianCorridorCache corridors = new PedestrianCorridorCache();
    private final PedestrianItineraryCache itineraries = new PedestrianItineraryCache();
    private java.util.Set<PedestrianDemandKey> retainedDemands = java.util.Set.of();
    // At most MAX_PENDING completed workers may await bounded result-buffer capacity.
    private final Map<PedestrianSearchTask, PedestrianRouteResult> awaitingPublication = new LinkedHashMap<>();
    private int retainedSurfaces;
    private int retainedRegions;
    private long workUnits;
    private boolean closed;
    private Object version;
    private long progressRevision;
    private long turns;
    private long completedCount;
    private java.util.Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> knowledgeDemandCache;
    private final Map<PedestrianRouteRequest, Long> admittedAt = new LinkedHashMap<>();
    private final Map<PedestrianDemandKey, java.util.Set<PedestrianRouteRequest>> calculationAddresses = new java.util.HashMap<>();
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
        var reversed = reverseResult(completed.entrySet(), geometry, start, target, extent);
        if (reversed.isPresent()) return reversed.orElseThrow();
        fairness.requested(request, service);
        rememberAddress(request, turns);
        boolean newDemand = false;
        if (!pending.containsKey(request)) {
            if (deferred.size() >= MAX_DEFERRED_DEMANDS && !deferred.containsKey(request)) {
                invalidated(request);
                return waiting("PLANNING_DEMAND_CAPACITY");
            }
            if (deferred.putIfAbsent(request, waiting("PLANNING_QUEUE_CAPACITY")) == null) { fairness.defer(request); newDemand = true; }
        }
        admitDeferred();
        if (newDemand) signalReady();
        return waiting(pending.containsKey(request) ? "PLANNING_QUEUED" : "PLANNING_QUEUE_CAPACITY");
    }

    public synchronized void advance(int workBudget) {
        advance(workBudget, () -> true);
    }
    /** Standalone callers open one finite turn. Native readiness may explicitly
     * retain the same allowance across callbacks without renewing it. */
    public synchronized void advance(int workBudget, java.util.function.BooleanSupplier mayContinue) {
        try (var allowance = new PedestrianPlanningAllowance(workBudget)) { advance(allowance, mayContinue); }
    }
    public synchronized void advance(PedestrianPlanningAllowance allowance, java.util.function.BooleanSupplier mayContinue) {
        java.util.Objects.requireNonNull(mayContinue); java.util.Objects.requireNonNull(allowance);
        if (closed) throw new IllegalStateException("pedestrian planner is closed");
        turns++;
        for (var entry : java.util.List.copyOf(awaitingPublication.entrySet())) {
            if (!mayContinue.getAsBoolean()) break;
            var task = entry.getKey();
            if (pending.get(task.request()) != task) awaitingPublication.remove(task);
            else complete(task.request(), task, entry.getValue());
        }
        admitDeferred();
        if (executor != null) { advanceWorkers(allowance, mayContinue); return; }
        int remaining = allowance.available();
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
            workUnits += spent; remaining -= spent; allowance.reserve(spent);
            if (spent > 0) progressRevision++;
            if (result.status() == PedestrianRouteResult.Status.PLANNING) { ready.addLast(request); continue; }
            complete(request, search, result);
        }
    }
    private void advanceWorkers(PedestrianPlanningAllowance allowance, java.util.function.BooleanSupplier mayContinue) {
        // Independent lanes do not fence each other. Within each lane retain
        // submission order; publication is server-owned, never a worker callback.
        int publications = MAX_PENDING;
        var blocked = java.util.EnumSet.noneOf(PedestrianPlanningFairness.Lane.class);
        for (var task : java.util.List.copyOf(inFlight)) {
            if (publications-- == 0 || !mayContinue.getAsBoolean()) break;
            var request = task.request();
            if (pending.get(request) != task) {
                // Cancellation retires authority, not the accepted executor slot.
                // Keep it occupied until that exact slice has actually returned;
                // otherwise rapid invalidations can overfill the bounded pool.
                var retired = task.poll();
                if (retired != null) { inFlight.remove(task); settle(task, retired.spent()); workUnits += retired.spent(); }
                continue;
            }
            var lane = fairness.lane(request);
            if (blocked.contains(lane)) continue;
            var step = task.poll();
            if (step == null) { blocked.add(lane); continue; }
            inFlight.remove(task); settle(task, step.spent()); workUnits += step.spent();
            if (step.spent() > 0) progressRevision++;
            if (step.result().status() == PedestrianRouteResult.Status.PLANNING) ready.addLast(request);
            else complete(request, task, step.result());
        }
        admitDeferred();
        int slice = HierarchicalPedestrianSearch.MIN_SLICE_WORK;
        while (allowance.available() >= slice && !ready.isEmpty() && inFlight.size() < MAX_PENDING && mayContinue.getAsBoolean()) {
            var request = fairness.take(ready); var task = pending.get(request);
            allowance.reserve(slice); reservations.put(task, new Reservation(allowance, slice));
            task.submit(executor, slice, this::signalReady); inFlight.addLast(task);
        }
    }
    private void settle(PedestrianSearchTask task, int spent) {
        var reservation = java.util.Objects.requireNonNull(reservations.remove(task), "worker slice lost its allowance");
        reservation.turn().settle(reservation.work(), spent);
    }
    private void complete(PedestrianRouteRequest request, PedestrianSearchTask search, PedestrianRouteResult result) {
        var dependency = search.dependencies();
        while (!completed.isEmpty() && (completed.size() >= MAX_RESULTS
                || retainedSurfaces + result.route().size() > MAX_RETAINED_SURFACES
                || retainedRegions + dependency.revisions().size() > MAX_RETAINED_REGIONS)) {
            var reusable = completed.entrySet().stream().filter(entry -> entry.getValue().result().awaitable()
                    || !retainedDemands.contains(PedestrianDemandKey.of(entry.getKey())))
                    .map(Map.Entry::getKey).findFirst();
            if (reusable.isEmpty()) {
                awaitingPublication.put(search, result);
                return; // Ready results belong to their waiting consumers, not an evictable cache.
            }
            var oldest = reusable.orElseThrow(); removeCompleted(oldest); invalidated(oldest);
        }
        awaitingPublication.remove(search); pending.remove(request);
        forgetAddress(request); completedCount++;
        corridors.remember(request.geometry(), result, dependency);
        completed.put(request, new Completed(result, dependency)); retainedSurfaces += result.route().size();
        knowledgeDemandCache = null;
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
            var admitted = fairness.nextAdmission(pending.keySet());
            if (pending.size() == MAX_PENDING) {
                var victim = fairness.yieldFor(admitted, pending.keySet(), ready);
                if (victim == null) break;
                // Saved searches own large bounded graphs. Keep at most another
                // active-window worth; evicting derived work grants no outcome.
                if (suspended.size() == MAX_PENDING) discardSuspended(suspended.keySet().iterator().next());
                suspended.put(victim, pending.remove(victim)); ready.remove(victim);
                deferred.put(victim, waiting("PLANNING_SERVICE_YIELD")); fairness.defer(victim);
            }
            deferred.remove(admitted); fairness.admit(admitted);
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
        for (var retained : java.util.List.copyOf(calculationAddresses.getOrDefault(PedestrianDemandKey.of(request), java.util.Set.of())))
            discardExact(retained);
    }
    private void discardExact(PedestrianRouteRequest request) {
        var search = pending.remove(request);
        if (search != null) { awaitingPublication.remove(search); search.cancel(); ready.remove(request); }
        deferred.remove(request); discardSuspended(request);
        if (admittedAt.containsKey(request)) invalidated(request);
    }
    /** Bounded missing facts from completed unknown results; no active worker is polled or surveyed. */
    public synchronized java.util.Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> knowledgeDemands() {
        if (knowledgeDemandCache != null) return knowledgeDemandCache;
        var positions = new java.util.LinkedHashSet<io.farfrontier.palemirror.frontier.v3.model.BlockPosition>();
        for (var value : completed.values()) positions.addAll(value.result().missingGeometry());
        knowledgeDemandCache = java.util.Set.copyOf(positions); return knowledgeDemandCache;
    }
    /** An actual geometry publication addresses dependent waits; a timer is not new knowledge. */
    public synchronized void geometryObserved(java.util.Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> positions) {
        for (var entry : java.util.List.copyOf(completed.entrySet())) {
            var result = entry.getValue().result();
            if (result.status() == PedestrianRouteResult.Status.UNKNOWN_GEOMETRY
                    && result.missingGeometry().stream().anyMatch(positions::contains)) {
                removeCompleted(entry.getKey()); invalidated(entry.getKey());
            }
        }
    }
    @Override public synchronized boolean hasCalculation(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                                         PedestrianRouteExtent extent) {
        return calculationAddresses.containsKey(PedestrianDemandKey.of(PedestrianRouteRequest.of(geometry, start, target, extent)));
    }
    /** Diagnostic counter only. Addressed changes, not this global counter, wake continuations. */
    public synchronized long progressRevision() { return progressRevision; }
    private void rememberAddress(PedestrianRouteRequest request, long since) {
        if (admittedAt.putIfAbsent(request, since) == null)
            calculationAddresses.computeIfAbsent(PedestrianDemandKey.of(request), ignored -> new java.util.LinkedHashSet<>()).add(request);
    }
    private void forgetAddress(PedestrianRouteRequest request) {
        if (admittedAt.remove(request) != null) {
            var key = PedestrianDemandKey.of(request); var addresses = calculationAddresses.get(key);
            addresses.remove(request); if (addresses.isEmpty()) calculationAddresses.remove(key);
        }
        fairness.remove(request);
    }
    private void invalidated(PedestrianRouteRequest request) { forgetAddress(request); changes.put(request, PedestrianPlanningChange.Kind.INVALIDATED); }
    /** A compatible immutable view is neither a result nor a lost dependency.
     * Consumers subscribe by complete demand scope and still revalidate current geometry. */
    private void readdress(PedestrianRouteRequest request, PedestrianRouteRequest replacement) {
        var address = fairness.capture(request);
        forgetAddress(request); fairness.restore(replacement, address);
        if (!request.sameDemand(replacement)) changes.put(request, PedestrianPlanningChange.Kind.INVALIDATED);
    }
    private void removeCompleted(PedestrianRouteRequest request) {
        knowledgeDemandCache = null;
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
            var search = pending.remove(request); readdress(request, replacement);
            if (search.rebase(replacement)) {
                pending.put(replacement, search);
                // Replacing an immutable view is not a new calculation turn.
                // Preserve round-robin position: rebuilding views every tick
                // must not reset rotation and starve all but its first search.
                var rotation = new ArrayDeque<PedestrianRouteRequest>();
                for (var queued : ready) rotation.addLast(queued.equals(request) ? replacement : queued);
                ready.clear(); ready.addAll(rotation);
                rememberAddress(replacement, since);
            } else { invalidated(request); fairness.remove(replacement); search.cancel(); ready.remove(request); }
            progressRevision++;
        }
        for (var request : java.util.List.copyOf(completed.keySet())) if (request.geometry() != geometry && sameScope(request, geometry)) {
            var result = completed.get(request); removeCompleted(request); forgetAddress(request);
            if (result.dependencies().matches(geometry)) {
                var replacement = PedestrianRouteRequest.of(geometry, request.start(), request.target(), request.extent());
                completed.put(replacement, result); retainedSurfaces += result.result().route().size();
                retainedRegions += result.dependencies().revisions().size();
            } else invalidated(request);
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
            readdress(request, replacement);
            var retained = suspended.remove(request);
            if (retained != null) {
                if (retained.rebase(replacement)) suspended.put(replacement, retained);
                else retained.cancel();
            }
            rebasedDeferred.put(replacement, entry.getValue()); rememberAddress(replacement, since); progressRevision++;
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
        var reversed = reverseResult(completed.entrySet(), geometry, start, target, extent);
        if (reversed.isPresent()) return reversed;
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
    /** Snapshot publication does not expose or mutate the live calculation queue. */
    @Override public synchronized PedestrianRoutePlanner readView() {
        var retained = java.util.List.copyOf(completed.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList());
        return new PedestrianRoutePlanner() {
            @Override public PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
                return query(geometry, start, target, PedestrianRouteExtent.COMPLETE);
            }
            @Override public PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                    PedestrianRouteExtent extent) {
                return peek(geometry, start, target, extent).orElseGet(() -> waiting("PLANNING_NOT_REQUESTED"));
            }
            @Override public java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry,
                    SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) {
                var request = PedestrianRouteRequest.of(geometry, start, target, extent);
                for (var entry : retained) if (entry.getKey().equals(request)
                        || entry.getKey().sameDemand(request) && entry.getValue().dependencies().matches(geometry))
                    return java.util.Optional.of(entry.getValue().result());
                return reverseResult(retained, geometry, start, target, extent);
            }
        };
    }
    private static java.util.Optional<PedestrianRouteResult> reverseResult(
            java.util.Collection<Map.Entry<PedestrianRouteRequest, Completed>> retained, PedestrianRouteGeometry geometry,
            SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) {
        for (var entry : retained) {
            var result = PedestrianLocalRouteReuse.reverse(entry.getKey(), entry.getValue().result(), geometry, start, target, extent);
            if (result.isPresent()) return result;
        }
        return java.util.Optional.empty();
    }

    /** The continuation owner supplies live demand. This retains results through
     * actual owner consumption; it neither starts work nor grants route authority. */
    public synchronized void retainDemands(java.util.Collection<PedestrianRouteRequest> requests) {
        if (requests.size() > MAX_DEFERRED_DEMANDS) throw new IllegalArgumentException("retained route demands exceed their metadata bound");
        var keys = new java.util.HashSet<PedestrianDemandKey>();
        requests.forEach(request -> keys.add(PedestrianDemandKey.of(request)));
        retainDemandKeys(keys);
    }
    /** Immutable derived subscription snapshot supplied by the continuation owner. */
    public synchronized void retainDemandKeys(java.util.Set<PedestrianDemandKey> keys) {
        if (keys.size() > MAX_DEFERRED_DEMANDS) throw new IllegalArgumentException("retained demands exceed metadata bound");
        retainedDemands = java.util.Set.copyOf(keys);
    }
    public synchronized int awaitingPublicationCount() { return awaitingPublication.size(); }
    public synchronized int retainedResultCount() { return completed.size(); }
    public synchronized int protectedResultCount() {
        return (int) completed.entrySet().stream().filter(entry -> !entry.getValue().result().awaitable()
                && retainedDemands.contains(PedestrianDemandKey.of(entry.getKey()))).count();
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
                                    long waitTurns, long searchWorkUnits, PedestrianRouteExtent extent, String stage, long restarts) { }
    public record KnowledgeDiagnostic(SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteResult result) { }
    public synchronized java.util.List<KnowledgeDiagnostic> knowledgeDiagnostics(int maximum) {
        if (maximum < 0 || maximum > MAX_RESULTS) throw new IllegalArgumentException("unbounded knowledge diagnostics");
        return completed.entrySet().stream().filter(entry -> entry.getValue().result().status() == PedestrianRouteResult.Status.UNKNOWN_GEOMETRY)
                .limit(maximum).map(entry -> new KnowledgeDiagnostic(entry.getKey().start(), entry.getKey().target(), entry.getValue().result())).toList();
    }
    /** Read-only bounded oldest-first view. It cannot advance, rebase, admit or wake anything. */
    public synchronized java.util.List<WaitingDiagnostic> waitingDiagnostics(int maximum) {
        if (maximum < 0 || maximum > MAX_PENDING + MAX_RESULTS)
            throw new IllegalArgumentException("waiting diagnostic limit exceeds the bounded queue");
        return admittedAt.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(maximum)
                .map(entry -> waitingDiagnostic(entry.getKey())).toList();
    }
    /** The bounded active window, independent of how many older demands await capacity. */
    public synchronized java.util.List<WaitingDiagnostic> activeDiagnostics() {
        return pending.keySet().stream().map(this::waitingDiagnostic).toList();
    }
    private WaitingDiagnostic waitingDiagnostic(PedestrianRouteRequest request) {
        var search = pending.get(request);
        return new WaitingDiagnostic(request.start(), request.target(), search == null,
                turns - admittedAt.get(request), search == null ? 0L : search.workUnits(), request.extent(),
                search == null ? "CAPACITY_WAIT" : search.stage(), search == null ? 0 : search.restarts());
    }
    public synchronized long completedCount() { return completedCount; }
    public synchronized long workUnits() { return workUnits; }
    public synchronized long corridorCandidates() { return corridors.hits(); }
    private static PedestrianRouteResult waiting(String reason) {
        return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, java.util.List.of(), 0L, 0, reason);
    }
    @Override public synchronized void close() {
        closed = true; readiness = () -> { }; reservations.clear(); pending.values().forEach(PedestrianSearchTask::cancel);
        inFlight.forEach(PedestrianSearchTask::cancel);
        suspended.values().forEach(PedestrianSearchTask::cancel); suspended.clear(); fairness.clear();
        pending.clear(); inFlight.clear(); ready.clear(); completed.clear(); deferred.clear(); admittedAt.clear(); calculationAddresses.clear(); changes.clear();
        awaitingPublication.clear(); retainedDemands = java.util.Set.of();
        regions.clear(); corridors.clear(); itineraries.clear(); retainedSurfaces = 0; retainedRegions = 0;
        synchronizedScopes.clear();
        knowledgeDemandCache = null;
    }
}
