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
        PedestrianRoutePlanner planner;
        synchronized (PedestrianRoutePlanning.class) { planner = current; }
        return planner.query(geometry, start, target);
    }

    /** The consumer declares a dependency only when an unresolved calculation actually prevents its result. */
    public static void await(PedestrianRouteRequest request) {
        var observation = observations.get();
        if (observation != null) observation.add(Objects.requireNonNull(request));
    }

    /** Captures declared pending dependencies of this caller, not diagnostics or unused alternatives. */
    public static <T> Observation<T> observe(java.util.function.Supplier<T> calculation) {
        var previous = observations.get();
        var requests = new java.util.LinkedHashSet<PedestrianRouteRequest>();
        observations.set(requests);
        try { return new Observation<>(calculation.get(), java.util.List.copyOf(requests)); }
        finally {
            if (previous == null) observations.remove();
            else { previous.addAll(requests); observations.set(previous); }
        }
    }
    public record Observation<T>(T result, java.util.List<PedestrianRouteRequest> requests) {
        public Observation { requests = java.util.List.copyOf(requests); }
    }

    /** Read retained evidence without starting a search. */
    public static java.util.Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
        PedestrianRoutePlanner planner;
        synchronized (PedestrianRoutePlanning.class) { planner = current; }
        return planner.peek(geometry, start, target);
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
        @Override public synchronized PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor start, SurfaceAnchor target) {
            if (version != geometry.version()) { views.clear(); regions.clear(); version = geometry.version(); }
            var results = views.get(geometry);
            if (results == null) {
                if (views.size() >= 32) views.clear();
                results = new LinkedHashMap<>(); views.put(geometry, results);
            }
            Endpoints key = new Endpoints(start, target);
            PedestrianRouteResult previous = results.get(key);
            if (previous != null) return previous;
            var search = new HierarchicalPedestrianSearch(geometry, start, target, regions);
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
    private record Endpoints(SurfaceAnchor start, SurfaceAnchor target) { }
}
