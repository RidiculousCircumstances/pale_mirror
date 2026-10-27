package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded derived validation, not route or actor authority. Cold replay on a miss validates every edge.
 * Exact consulted regional facts (profile and permissions included) fence reuse; nonregional providers keep full-version validation. */
public final class PedestrianPathValidation {
    private static final int MAX_PATHS = 128;
    private sealed interface Validation permits Exact, Regional {
        boolean matches(PedestrianRouteGeometry geometry);
    }
    private record Exact(Object version) implements Validation {
        @Override public boolean matches(PedestrianRouteGeometry geometry) { return version.equals(geometry.version()); }
    }
    private record Regional(io.farfrontier.palemirror.frontier.v3.model.WorldBounds bounds,
                            PedestrianGeometryRevision revision, java.util.Set<PedestrianGeometryRevision.Region> dependencies) implements Validation {
        @Override public boolean matches(PedestrianRouteGeometry geometry) {
            return bounds.equals(geometry.bounds()) && geometry.regionalRevision()
                    .filter(current -> current.matches(revision, dependencies)).isPresent();
        }
    }
    private static final Map<PedestrianPathProof, Validation> VALIDATED = new LinkedHashMap<>(16, .75f, true);
    private PedestrianPathValidation() { }
    public static boolean supports(PedestrianRouteGeometry geometry, PedestrianPathProof path) {
        Objects.requireNonNull(geometry.version());
        Validation prior;
        synchronized (VALIDATED) { prior = VALIDATED.get(path); }
        if (prior != null && prior.matches(geometry)) return true;
        if (!KnownPedestrianNavigation.supportsRoute(geometry, path.surfaces())) return false;
        var captured = capture(geometry, path);
        synchronized (VALIDATED) {
            VALIDATED.put(path, captured);
            if (VALIDATED.size() > MAX_PATHS) VALIDATED.remove(VALIDATED.keySet().iterator().next());
        }
        return true;
    }
    private static Validation capture(PedestrianRouteGeometry geometry, PedestrianPathProof path) {
        var regional = geometry.regionalRevision();
        if (regional.isEmpty()) return new Exact(geometry.version());
        var dependencies = new java.util.HashSet<PedestrianGeometryRevision.Region>();
        var position = path.origin(); dependencies.add(PedestrianGeometryRevision.Region.at(position.support()));
        // A run changes one horizontal coordinate. Visit crossed regions rather
        // than expanding its every block again after exact geometry validation.
        for (var run : path.runs()) {
            var end = run.end(position, run.edges());
            var from = PedestrianGeometryRevision.Region.at(position.support());
            var to = PedestrianGeometryRevision.Region.at(end.support());
            int x = from.x(), z = from.z();
            while (x != to.x() || z != to.z()) {
                x += Integer.compare(to.x(), x); z += Integer.compare(to.z(), z);
                dependencies.add(new PedestrianGeometryRevision.Region(x, z));
                if (dependencies.size() > PedestrianGeometryRevision.MAX_REGIONS) return new Exact(geometry.version());
            }
            position = end;
        }
        var footprint = java.util.Set.copyOf(dependencies);
        return new Regional(geometry.bounds(), regional.orElseThrow().restrictedTo(footprint), footprint);
    }
    public static void require(PedestrianRouteGeometry geometry, PedestrianPathProof path) {
        if (!supports(geometry, path))
            KnownPedestrianNavigation.requireRoute(geometry, path.surfaces()); // Preserve the exact failing cell in diagnostics.
    }
}
