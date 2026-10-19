package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded derived validation, not route or actor authority. Cold replay on a miss validates every edge.
 * Keys include the complete geometry version (profile and permissions included), never just endpoints. */
public final class PedestrianPathValidation {
    private static final int MAX_PATHS = 128;
    private static final Map<PedestrianPathProof, Object> VALIDATED = new LinkedHashMap<>(16, .75f, true);
    private PedestrianPathValidation() { }
    public static boolean supports(PedestrianRouteGeometry geometry, PedestrianPathProof path) {
        var version = Objects.requireNonNull(geometry.version());
        synchronized (VALIDATED) { if (version.equals(VALIDATED.get(path))) return true; }
        if (!KnownPedestrianNavigation.supportsRoute(geometry, path.surfaces())) return false;
        synchronized (VALIDATED) {
            VALIDATED.put(path, version);
            if (VALIDATED.size() > MAX_PATHS) VALIDATED.remove(VALIDATED.keySet().iterator().next());
        }
        return true;
    }
    public static void require(PedestrianRouteGeometry geometry, PedestrianPathProof path) {
        if (!supports(geometry, path))
            KnownPedestrianNavigation.requireRoute(geometry, path.surfaces()); // Preserve the exact failing cell in diagnostics.
    }
}
