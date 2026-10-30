package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit calculation binding; adapters may supply cooperative planning, never terrain or a body writer. */
public final class PedestrianRoutePlanning {
    private static final PedestrianRoutePlanner DIRECT = new CachedDirectPlanner();
    private static PedestrianRoutePlanner current = DIRECT;
    /** One stable context per computing thread. Scoped values are restored even
     * on failure; the empty context retains no calculation, geometry or world. */
    private static final ThreadLocal<Context> CONTEXT = ThreadLocal.withInitial(Context::new);
    private static final class Context {
        java.util.LinkedHashSet<PedestrianRouteRequest> observations, acquired;
        PedestrianRoutePlanner readBinding;
        PedestrianCalculationMemory.Builder completedReads;
        Inspection inspection;
        PedestrianPlanningService service;
    }
    private static final class Inspection { private boolean unresolved; }
    public enum Readiness { READY, UNAVAILABLE, UNRESOLVED }
    private PedestrianRoutePlanning() { }

    public static synchronized AutoCloseable bind(PedestrianRoutePlanner planner) {
        if (current != DIRECT) throw new IllegalStateException("known navigation already has a planning owner");
        current = Objects.requireNonNull(planner);
        return () -> { synchronized (PedestrianRoutePlanning.class) {
            if (current != planner) throw new IllegalStateException("planning release has a foreign owner");
            current = DIRECT;
        } };
    }
    public static PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return query(geometry, start, target, PedestrianRouteExtent.COMPLETE);
    }
    public static PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                              PedestrianRouteExtent extent) {
        var context = CONTEXT.get();
        var probe = context.inspection;
        if (probe != null) {
            var result = peek(geometry, start, target, extent).orElseGet(() -> new PedestrianRouteResult(
                    PedestrianRouteResult.Status.PLANNING, java.util.List.of(), 0L, 0, "PLANNING_NOT_REQUESTED"));
            if (result.status() != PedestrianRouteResult.Status.FOUND && result.status() != PedestrianRouteResult.Status.SEGMENT_READY
                    && result.status() != PedestrianRouteResult.Status.NO_PATH
                    && result.status() != PedestrianRouteResult.Status.ESTIMATED)
                probe.unresolved = true;
            return result;
        }
        PedestrianRoutePlanner planner;
        synchronized (PedestrianRoutePlanning.class) { planner = context.readBinding == null ? current : context.readBinding; }
        var acquisition = context.acquired;
        boolean created = acquisition != null && !planner.hasCalculation(geometry, start, target, extent);
        var result = planner.query(geometry, start, target, extent,
                context.service == null ? PedestrianPlanningService.ORDINARY : context.service);
        var reads = context.completedReads;
        if (reads != null && PedestrianCalculationMemory.capturable(result)) {
            var request = PedestrianRouteRequest.of(geometry, start, target, extent);
            reads.add(planner.readFact(request).orElseGet(() -> PedestrianCalculationMemory.Read.conservative(request, result)));
        }
        if (created && result.status() == PedestrianRouteResult.Status.PLANNING)
            acquisition.add(PedestrianRouteRequest.of(geometry, start, target, extent));
        return result;
    }

    /** Current completed evidence only, for acknowledged delivery to an exact waiting caller. */
    public static java.util.Optional<PedestrianCalculationMemory.Read> readFact(PedestrianRouteRequest request) {
        var context = CONTEXT.get();
        PedestrianRoutePlanner planner;
        synchronized (PedestrianRoutePlanning.class) { planner = context.readBinding == null ? current : context.readBinding; }
        return planner.readFact(request);
    }
    /** Capture on the owner thread; run advice without acquiring live route work. */
    public static synchronized PedestrianRoutePlanner readView() { return current.readView(); }
    public static <T> Prepared<T> prepare(PedestrianRoutePlanner view, java.util.function.Supplier<T> calculation) {
        return prepare(view, PedestrianCalculationMemory.empty(), calculation);
    }
    public static <T> Prepared<T> prepare(PedestrianRoutePlanner view, PedestrianCalculationMemory memory,
            java.util.function.Supplier<T> calculation) {
        var context = CONTEXT.get();
        var demands = new LinkedHashMap<PedestrianRouteRequest, PedestrianPlanningService>();
        var previous = context.readBinding;
        context.readBinding = new PedestrianRoutePlanner() {
            @Override public PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
                return query(geometry, start, target, PedestrianRouteExtent.COMPLETE, PedestrianPlanningService.ORDINARY);
            }
            @Override public PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                    PedestrianRouteExtent extent, PedestrianPlanningService requirement) {
                var request = PedestrianRouteRequest.of(geometry, start, target, extent);
                demands.put(request, requirement);
                var prior = memory.find(request);
                if (prior.isPresent()) return prior.orElseThrow().result();
                return view.query(geometry, start, target, extent);
            }
            @Override public java.util.Optional<PedestrianCalculationMemory.Read> readFact(PedestrianRouteRequest request) {
                return memory.find(request).or(() -> view.readFact(request));
            }
            @Override public java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry,
                    SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) {
                return memory.find(PedestrianRouteRequest.of(geometry, start, target, extent)).map(PedestrianCalculationMemory.Read::result)
                        .or(() -> view.peek(geometry, start, target, extent));
            }
        };
        try {
            var observed = observe(calculation);
            return new Prepared<>(new Observation<>(observed.result(), observed.requests(), observed.update().basedOn(memory)), Map.copyOf(demands));
        }
        finally { context.readBinding = previous; }
    }
    public record Prepared<T>(Observation<T> observation, Map<PedestrianRouteRequest, PedestrianPlanningService> demands) {
        public Prepared { Objects.requireNonNull(observation); demands = Map.copyOf(demands); }
        public PedestrianCalculationMemory reads() { return observation.reads(); }
        public PedestrianCalculationMemory.Update update() { return observation.update(); }
        /** Owner-side delivery may have advanced since this detached view was captured. */
        public java.util.List<PedestrianPlanningChange> acquire(PedestrianCalculationMemory delivered) {
            return withReadMemory(delivered, this::acquire);
        }
        /** Accepted advice alone may request retained dependencies. Cancelled advice has no effects. */
        public java.util.List<PedestrianPlanningChange> acquire() {
            var ready = new java.util.ArrayList<PedestrianPlanningChange>();
            for (var request : observation.requests()) {
                var result = withService(demands.getOrDefault(request, PedestrianPlanningService.ORDINARY),
                        () -> query(request.geometry(), request.start(), request.target(), request.extent()));
                // Re-reading a retained knowledge gap does not complete its wait.
                // Actual search completion/geometry invalidation already signals
                // the subscribed demand through the planner's change stream.
                if (!result.awaitable())
                    ready.add(new PedestrianPlanningChange(request, PedestrianPlanningChange.Kind.RESULT_AVAILABLE));
            }
            return java.util.List.copyOf(ready);
        }
    }

    /** Lexically scoped operational requirement. Does not alter the calculation/cache identity. */
    public static <T> T withService(PedestrianPlanningService requirement, java.util.function.Supplier<T> calculation) {
        var context = CONTEXT.get();
        Objects.requireNonNull(requirement); var previous = context.service; context.service = requirement;
        try { return calculation.get(); }
        finally { context.service = previous; }
    }

    /** The consumer declares a dependency only when an unresolved calculation actually prevents its result. */
    public static void await(PedestrianRouteRequest request) {
        var context = CONTEXT.get();
        if (context.inspection != null) return; // Inspection owns no calculation or continuation.
        var observation = context.observations;
        if (observation != null) observation.add(Objects.requireNonNull(request));
    }

    /** Explicit cursor checkpoint for a resumable calculation: the caller has
     * finished its previous phase and can resume without replaying that prefix. A suspended
     * phase must retain its reads. This never evicts shared results or demands.
     * Root-replayed calculations do not call this capability. */
    public static void retireCompletedReadPhase() {
        var reads = CONTEXT.get().completedReads;
        if (reads != null) reads.retirePrefix();
    }

    /** Bounded read-only opportunity probe. Reuse family predicates through a
     * retained-only calculation capability, without speculative searches or wakes.
     * Only the actual chosen process may acquire the ordinary query capability. */
    public static Readiness inspectAvailability(java.util.function.BooleanSupplier opportunity) {
        var context = CONTEXT.get();
        var previous = context.inspection;
        var probe = new Inspection(); context.inspection = probe;
        try {
            boolean available;
            try { available = opportunity.getAsBoolean(); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) probe.unresolved = true;
                available = false;
            }
            return available ? Readiness.READY : probe.unresolved ? Readiness.UNRESOLVED : Readiness.UNAVAILABLE;
        } finally {
            if (previous == null) context.inspection = null;
            else { previous.unresolved |= probe.unresolved; context.inspection = previous; }
        }
    }

    /** Resume one caller's completed reads while fresh queries still use the live
     * capability. Missing facts cannot be supplied by calculation memory. */
    public static <T> Observation<T> observe(PedestrianCalculationMemory memory, java.util.function.Supplier<T> calculation) {
        var observed = withReadMemory(memory, () -> observe(calculation));
        return new Observation<>(observed.result(), observed.requests(), observed.update().basedOn(memory));
    }
    /** Does not establish an observation/acquisition scope: delivery must not
     * accidentally abandon a newly acquired, still required dependency. */
    private static <T> T withReadMemory(PedestrianCalculationMemory memory, java.util.function.Supplier<T> calculation) {
        var context = CONTEXT.get();
        var previous = context.readBinding; PedestrianRoutePlanner delegate;
        synchronized (PedestrianRoutePlanning.class) { delegate = previous == null ? current : previous; }
        context.readBinding = new Remembering(delegate, memory);
        try { return calculation.get(); }
        finally { context.readBinding = previous; }
    }
    private record Remembering(PedestrianRoutePlanner delegate, PedestrianCalculationMemory memory) implements PedestrianRoutePlanner {
        public PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
            return query(geometry, start, target, PedestrianRouteExtent.COMPLETE, PedestrianPlanningService.ORDINARY);
        }
        public PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                PedestrianRouteExtent extent, PedestrianPlanningService service) {
            return memory.find(PedestrianRouteRequest.of(geometry, start, target, extent)).map(PedestrianCalculationMemory.Read::result)
                    .orElseGet(() -> delegate.query(geometry, start, target, extent, service));
        }
        public java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                PedestrianRouteExtent extent) {
            return memory.find(PedestrianRouteRequest.of(geometry, start, target, extent)).map(PedestrianCalculationMemory.Read::result)
                    .or(() -> delegate.peek(geometry, start, target, extent));
        }
        public java.util.Optional<PedestrianCalculationMemory.Read> readFact(PedestrianRouteRequest request) {
            return memory.find(request).or(() -> delegate.readFact(request));
        }
        public boolean hasCalculation(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) {
            return memory.find(PedestrianRouteRequest.of(geometry, start, target, extent)).isPresent()
                    || delegate.hasCalculation(geometry, start, target, extent);
        }
        public void discardUnobserved(PedestrianRouteRequest request) { delegate.discardUnobserved(request); }
    }

    /** Captures declared pending dependencies of this caller, not diagnostics or unused alternatives. */
    public static <T> Observation<T> observe(java.util.function.Supplier<T> calculation) {
        var context = CONTEXT.get();
        var previous = context.observations;
        var previousAcquired = context.acquired;
        var previousReads = context.completedReads;
        var reads = new PedestrianCalculationMemory.Builder(); context.completedReads = reads;
        var created = new java.util.LinkedHashSet<PedestrianRouteRequest>(); context.acquired = created;
        var requests = new java.util.LinkedHashSet<PedestrianRouteRequest>();
        context.observations = requests;
        try { var result = calculation.get(); return new Observation<>(result, java.util.List.copyOf(requests), reads.update(PedestrianCalculationMemory.empty())); }
        finally {
            context.completedReads = previousReads;
            context.acquired = previousAcquired;
            context.observations = previous;
            if (previousReads != null) previousReads.add(reads.update(PedestrianCalculationMemory.empty()));
            PedestrianRoutePlanner planner;
            synchronized (PedestrianRoutePlanning.class) { planner = context.readBinding == null ? current : context.readBinding; }
            // Newly acquired alternatives alone are disposable. Context restoration
            // precedes cleanup, whose owner callback may itself throw or reenter.
            for (var request : created) if (!requests.contains(request)) planner.discardUnobserved(request);
            if (previousAcquired != null) { created.retainAll(requests); previousAcquired.addAll(created); }
            if (previous != null) previous.addAll(requests);
        }
    }
    public record Observation<T>(T result, java.util.List<PedestrianRouteRequest> requests, PedestrianCalculationMemory.Update update) {
        public Observation(T result, java.util.List<PedestrianRouteRequest> requests) { this(result, requests, PedestrianCalculationMemory.empty()); }
        public Observation(T result, java.util.List<PedestrianRouteRequest> requests, PedestrianCalculationMemory reads) {
            this(result, requests, PedestrianCalculationMemory.Update.adding(reads));
        }
        public Observation { requests = java.util.List.copyOf(requests); Objects.requireNonNull(update); }
        public PedestrianCalculationMemory reads() { return update.result(); }
    }

    /** Read retained evidence without starting a search. */
    public static java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return peek(geometry, start, target, PedestrianRouteExtent.COMPLETE);
    }
    public static java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                                                PedestrianRouteExtent extent) {
        var context = CONTEXT.get();
        PedestrianRoutePlanner planner;
        synchronized (PedestrianRoutePlanning.class) { planner = context.readBinding == null ? current : context.readBinding; }
        return planner.peek(geometry, start, target, extent);
    }

    /** Deterministic calculation for synchronous owners and isolated replay, using the same search. */
    public static PedestrianRouteResult calculate(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return DIRECT.query(geometry, start, target);
    }

    /** Pure deterministic execution for isolated engines; caches change cost, never readiness. */
    private static final class CachedDirectPlanner implements PedestrianRoutePlanner {
        private final Map<PedestrianRouteGeometry, Map<Endpoints, PedestrianRouteResult>> views = new java.util.WeakHashMap<>();
        private record RegionalQuery(PedestrianGeometryRevision revision,
                io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds, Endpoints endpoints) { }
        // Full declared query revision, not just the traversed corridor: even a
        // negative result or a cheaper newly opened alternative must invalidate.
        private final Map<RegionalQuery, PedestrianRouteResult> regional = new LinkedHashMap<>();
        private final PedestrianRegionCache regions = new PedestrianRegionCache();
        private Object version;
        @Override public synchronized java.util.Optional<PedestrianRouteResult> peek(
                PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
            return peek(geometry, start, target, PedestrianRouteExtent.COMPLETE);
        }
        @Override public synchronized java.util.Optional<PedestrianRouteResult> peek(
                PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) {
            if (geometry.regionalRevision().isPresent()) return java.util.Optional.ofNullable(regional.get(
                    new RegionalQuery(geometry.regionalRevision().orElseThrow(), geometry.bounds(), new Endpoints(start, target, extent))));
            if (geometry.regionalRevision().isEmpty() && version != geometry.version()) return java.util.Optional.empty();
            var results = views.get(geometry);
            return results == null ? java.util.Optional.empty()
                    : java.util.Optional.ofNullable(results.get(new Endpoints(start, target, extent)));
        }
        @Override public synchronized PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
            return query(geometry, start, target, PedestrianRouteExtent.COMPLETE);
        }
        @Override public synchronized PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                                                 PedestrianRouteExtent extent) {
            var retained = peek(geometry, start, target, extent);
            if (retained.isPresent()) return retained.orElseThrow();
            if (geometry.regionalRevision().isEmpty() && version != geometry.version()) {
                views.keySet().removeIf(view -> view.regionalRevision().isEmpty()); version = geometry.version();
            }
            Endpoints key = new Endpoints(start, target, extent);
            var search = new HierarchicalPedestrianSearch(geometry, start, target, regions, null, extent);
            PedestrianRouteResult result;
            do { result = search.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2); }
            while (result.status() == PedestrianRouteResult.Status.PLANNING);
            int count = regional.size() + views.values().stream().mapToInt(Map::size).sum();
            int surfaces = regional.values().stream().mapToInt(value -> value.route().size()).sum()
                    + views.values().stream().flatMap(value -> value.values().stream()).mapToInt(value -> value.route().size()).sum();
            if (count >= 128 || surfaces + result.route().size() > 131_072) {
                views.clear(); regional.clear();
            }
            if (geometry.regionalRevision().isPresent())
                regional.put(new RegionalQuery(geometry.regionalRevision().orElseThrow(), geometry.bounds(), key), result);
            else {
                if (views.size() >= 32 && !views.containsKey(geometry)) views.clear();
                views.computeIfAbsent(geometry, ignored -> new LinkedHashMap<>()).put(key, result);
            }
            return result;
        }
    }
    private record Endpoints(SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) { }
}
