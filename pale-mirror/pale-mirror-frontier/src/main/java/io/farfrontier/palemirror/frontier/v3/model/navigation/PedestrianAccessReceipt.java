package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Bounded access evidence for an admitted area. Its enclosing owner/version supplies authority. */
public record PedestrianAccessReceipt(SurfaceAnchor origin, Map<SurfaceAnchor, Optional<PedestrianRouteReceipt>> routes) {
    public static final int MAX_DESTINATIONS = 256;
    public PedestrianAccessReceipt {
        Objects.requireNonNull(origin); routes = Map.copyOf(routes);
        if (routes.isEmpty() || routes.size() > MAX_DESTINATIONS) throw new IllegalArgumentException("unbounded area access evidence");
        for (var entry : routes.entrySet()) {
            var path = entry.getValue().map(PedestrianRouteReceipt::route).orElseGet(() -> List.of(origin));
            if (!path.getFirst().equals(origin) || !path.getLast().equals(entry.getKey()))
                throw new IllegalArgumentException("area access evidence has foreign endpoints");
        }
    }
    /** Geometry validation only: no planner, ranking or admission/custody side effect. */
    public void require(PedestrianRouteGeometry geometry, SurfaceAnchor expectedOrigin, Set<SurfaceAnchor> destinations) {
        if (!origin.equals(expectedOrigin) || !routes.keySet().equals(destinations))
            throw new IllegalArgumentException("area access evidence has a changed departure or declared destination set");
        for (var receipt : routes.values())
            KnownPedestrianNavigation.requireRoute(geometry, receipt.map(PedestrianRouteReceipt::route).orElseGet(() -> List.of(origin)));
    }
    public static PedestrianAccessReceipt selected(SurfaceAnchor origin,
            Map<SurfaceAnchor, Optional<PedestrianRouteReceipt>> calculated, Set<SurfaceAnchor> destinations) {
        var selected = new LinkedHashMap<SurfaceAnchor, Optional<PedestrianRouteReceipt>>();
        for (var destination : destinations) {
            var receipt = calculated.get(destination);
            if (receipt == null) throw new IllegalArgumentException("area selection has no accepted access proof");
            selected.put(destination, receipt);
        }
        return new PedestrianAccessReceipt(origin, selected);
    }
}
