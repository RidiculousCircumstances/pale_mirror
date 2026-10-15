package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit calculation binding; adapters may supply cooperative planning, never terrain or a body writer. */
public final class PedestrianRoutePlanning {
    private static final PedestrianRoutePlanner DIRECT = new CachedDirectPlanner();
    private static PedestrianRoutePlanner current = DIRECT;
    private static final ThreadLocal<java.util.LinkedHashSet<PedestrianRouteRequest>> observations = new ThreadLocal<>();
    private static final ThreadLocal<Inspection> inspection = new ThreadLocal<>();
    private static final ThreadLocal<PedestrianPlanningService> service = new ThreadLocal<>();
    private static final ThreadLocal<java.util.LinkedHashSet<PedestrianRouteRequest>> acquired = new ThreadLocal<>();
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
        var probe = inspection.get();
        if (probe != null) {
            var result = peek(geometry, start, target, extent).orElseGet(() -> new PedestrianRouteResult(
                    PedestrianRouteResult.Status.PLANNING, java.util.List.of(), 0L, 0, "PLANNING_NOT_REQUESTED"));
            if (result.status() != PedestrianRouteResult.Status.FOUND && result.status() != PedestrianRouteResult.Status.SEGMENT_READY
                    && result.status() != PedestrianRouteResult.Status.NO_PATH)
                probe.unresolved = true;
            return result;
        }
        PedestrianRoutePlanner planner;
        synchronized (PedestrianRoutePlanning.class) { planner = current; }
        var acquisition = acquired.get();
        boolean created = acquisition != null && !planner.hasCalculation(geometry, start, target, extent);
        var result = planner.query(geometry, start, target, extent,
                service.get() == null ? PedestrianPlanningService.ORDINARY : service.get());
        if (created && result.status() == PedestrianRouteResult.Status.PLANNING)
            acquisition.add(PedestrianRouteRequest.of(geometry, start, target, extent));
        return result;
    }

    /** Lexically scoped operational requirement. Does not alter the calculation/cache identity. */
    public static <T> T withService(PedestrianPlanningService requirement, java.util.function.Supplier<T> calculation) {
        Objects.requireNonNull(requirement); var previous = service.get(); service.set(requirement);
        try { return calculation.get(); }
        finally { if (previous == null) service.remove(); else service.set(previous); }
    }

    /** The consumer declares a dependency only when an unresolved calculation actually prevents its result. */
    public static void await(PedestrianRouteRequest request) {
        if (inspection.get() != null) return; // Inspection owns no calculation or continuation.
        var observation = observations.get();
        if (observation != null) observation.add(Objects.requireNonNull(request));
    }

    /** Bounded read-only opportunity probe. Reuse family predicates through a
     * retained-only calculation capability, without speculative searches or wakes.
     * Only the actual chosen process may acquire the ordinary query capability. */
    public static Readiness inspectAvailability(java.util.function.BooleanSupplier opportunity) {
        var previous = inspection.get();
        var probe = new Inspection(); inspection.set(probe);
        try {
            boolean available;
            try { available = opportunity.getAsBoolean(); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) probe.unresolved = true;
                available = false;
            }
            return available ? Readiness.READY : probe.unresolved ? Readiness.UNRESOLVED : Readiness.UNAVAILABLE;
        } finally {
            if (previous == null) inspection.remove();
            else { previous.unresolved |= probe.unresolved; inspection.set(previous); }
        }
    }

    /** Captures declared pending dependencies of this caller, not diagnostics or unused alternatives. */
    public static <T> Observation<T> observe(java.util.function.Supplier<T> calculation) {
        var previous = observations.get();
        var previousAcquired = acquired.get();
        var created = new java.util.LinkedHashSet<PedestrianRouteRequest>(); acquired.set(created);
        var requests = new java.util.LinkedHashSet<PedestrianRouteRequest>();
        observations.set(requests);
        try { return new Observation<>(calculation.get(), java.util.List.copyOf(requests)); }
        finally {
            PedestrianRoutePlanner planner;
            synchronized (PedestrianRoutePlanning.class) { planner = current; }
            // A newly launched alternative which the selector did not retain
            // has no other caller yet. Never cancel pre-existing/shared demand.
            for (var request : created) if (!requests.contains(request)) planner.discardUnobserved(request);
            if (previousAcquired == null) acquired.remove();
            else { created.retainAll(requests); previousAcquired.addAll(created); acquired.set(previousAcquired); }
            if (previous == null) observations.remove();
            else { previous.addAll(requests); observations.set(previous); }
        }
    }
    public record Observation<T>(T result, java.util.List<PedestrianRouteRequest> requests) {
        public Observation { requests = java.util.List.copyOf(requests); }
    }

    /** Read retained evidence without starting a search. */
    public static java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return peek(geometry, start, target, PedestrianRouteExtent.COMPLETE);
    }
    public static java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target,
                                                                PedestrianRouteExtent extent) {
        PedestrianRoutePlanner planner;
        synchronized (PedestrianRoutePlanning.class) { planner = current; }
        return planner.peek(geometry, start, target, extent);
    }

    /** Deterministic calculation for synchronous owners and isolated replay, using the same search. */
    public static PedestrianRouteResult calculate(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        return DIRECT.query(geometry, start, target);
    }

    /** Pure deterministic execution for isolated engines; caches change cost, never readiness. */
    private static final class CachedDirectPlanner implements PedestrianRoutePlanner {
        private final Map<PedestrianRouteGeometry, Map<Endpoints, PedestrianRouteResult>> views = new java.util.WeakHashMap<>();
        private final PedestrianRegionCache regions = new PedestrianRegionCache();
        private Object version;
        @Override public synchronized java.util.Optional<PedestrianRouteResult> peek(
                PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
            return peek(geometry, start, target, PedestrianRouteExtent.COMPLETE);
        }
        @Override public synchronized java.util.Optional<PedestrianRouteResult> peek(
                PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) {
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
            if (geometry.regionalRevision().isEmpty() && version != geometry.version()) {
                views.keySet().removeIf(view -> view.regionalRevision().isEmpty()); version = geometry.version();
            }
            var results = views.get(geometry);
            if (results == null) {
                if (views.size() >= 32) views.clear();
                results = new LinkedHashMap<>(); views.put(geometry, results);
            }
            Endpoints key = new Endpoints(start, target, extent);
            PedestrianRouteResult previous = results.get(key);
            if (previous != null) return previous;
            var search = new HierarchicalPedestrianSearch(geometry, start, target, regions, null, extent);
            PedestrianRouteResult result;
            do { result = search.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2); }
            while (result.status() == PedestrianRouteResult.Status.PLANNING);
            int count = views.values().stream().mapToInt(Map::size).sum();
            int surfaces = views.values().stream().flatMap(value -> value.values().stream()).mapToInt(value -> value.route().size()).sum();
            if (count >= 128 || surfaces + result.route().size() > 131_072) {
                views.clear(); results = new LinkedHashMap<>(); views.put(geometry, results);
            }
            results.put(key, result); return result;
        }
    }
    private record Endpoints(SurfaceAnchor start, SurfaceAnchor target, PedestrianRouteExtent extent) { }
}
