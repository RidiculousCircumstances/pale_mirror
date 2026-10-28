package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded derived validation, not route or actor authority. A miss proves all edges through exact cell checks or an explicit producer certificate.
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
    /** Cell-list consumers use the same complete geometry proof as whole journeys.
     * A malformed advisory list is unusable evidence, not a failed semantic order. */
    public static boolean supportsRoute(PedestrianRouteGeometry geometry,
            java.util.List<io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor> route) {
        PedestrianPathProof path;
        try { path = PedestrianPathProof.from(route); }
        catch (IllegalArgumentException | ArithmeticException malformed) { return false; }
        return supports(geometry, path);
    }
    public static boolean supports(PedestrianRouteGeometry geometry, PedestrianPathProof path) {
        Objects.requireNonNull(geometry.version());
        Validation prior;
        synchronized (VALIDATED) { prior = VALIDATED.get(path); }
        if (!io.farfrontier.palemirror.frontier.v3.model.ImmutableValidationScope.fullAudit()
                && prior != null && prior.matches(geometry)) return true;
        if (!(io.farfrontier.palemirror.frontier.v3.model.ImmutableValidationScope.fullAudit()
                ? KnownPedestrianNavigation.supportsRoute(geometry, path.surfaces()) : validateGeometry(geometry, path))) return false;
        var captured = capture(geometry, path);
        synchronized (VALIDATED) {
            VALIDATED.put(path, captured);
            if (VALIDATED.size() > MAX_PATHS) VALIDATED.remove(VALIDATED.keySet().iterator().next());
        }
        return true;
    }
    /** The same optional producer certificate used by the regional graph proves
     * every horizontal edge in its rectangle for this exact body and permission
     * image. Missing certificates retain the complete cell/edge checker. */
    private static boolean validateGeometry(PedestrianRouteGeometry geometry, PedestrianPathProof path) {
        var position = path.origin();
        if (path.runs().isEmpty()) return validSurface(geometry, position);
        for (var run : path.runs()) {
            int remaining = run.edges();
            while (remaining > 0) {
                int edges = Math.min(remaining, PedestrianRegion.SIDE);
                var end = run.end(position, edges);
                boolean certified = false;
                if (run.dy() == 0 && geometry.bounds().contains(position.support()) && geometry.bounds().contains(end.support())) {
                    var area = new io.farfrontier.palemirror.frontier.v3.model.WorldBounds(
                            Math.min(position.x(), end.x()), Math.min(position.z(), end.z()),
                            Math.abs(end.x() - position.x()) + 1, Math.abs(end.z() - position.z()) + 1);
                    var height = geometry.uniformSupport(area);
                    certified = height.isPresent() && height.orElseThrow() == position.y();
                }
                if (!certified) {
                    if (!validSurface(geometry, position)) return false;
                    for (int step = 0; step < edges; step++) {
                        var next = run.end(position, 1);
                        if (!validSurface(geometry, next) || !geometry.canTraverse(position, next)) return false;
                        position = next;
                    }
                } else position = end;
                remaining -= edges;
            }
        }
        return true;
    }
    private static boolean validSurface(PedestrianRouteGeometry geometry,
            io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor surface) {
        return geometry.bounds().contains(surface.support()) && geometry.knowsSupport(surface) && !geometry.blocked(surface);
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
            KnownPedestrianNavigation.requireRouteExact(geometry, path.surfaces()); // Preserve the exact failing cell in diagnostics.
    }
}
