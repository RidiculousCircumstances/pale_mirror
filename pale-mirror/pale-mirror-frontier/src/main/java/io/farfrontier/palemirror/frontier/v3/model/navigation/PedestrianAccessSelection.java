package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** One ephemeral selection over the caller's bounded alternatives, not a route or admission owner. */
public final class PedestrianAccessSelection {
    private final PedestrianRouteGeometry geometry;
    private final SurfaceAnchor origin;
    private final Map<SurfaceAnchor, PedestrianRouteResult> results = new LinkedHashMap<>();
    private final Map<SurfaceAnchor, Optional<PedestrianRouteReceipt>> accepted = new LinkedHashMap<>();

    public PedestrianAccessSelection(PedestrianRouteGeometry geometry, SurfaceAnchor origin) {
        this.geometry = Objects.requireNonNull(geometry); this.origin = Objects.requireNonNull(origin);
    }
    /** An unresolved alternative cannot prevent a different supported alternative from being selected. */
    public boolean reachable(SurfaceAnchor target) {
        var result = results.get(target);
        if (result == null) {
            if (results.values().stream().anyMatch(value -> value.status() == PedestrianRouteResult.Status.PLANNING)
                    && PedestrianRoutePlanning.peek(geometry, origin, target).isEmpty()) return false;
            try { result = KnownPedestrianNavigation.queryBetween(geometry, origin, target); }
            catch (KnownPedestrianNavigation.RouteUnavailable blocked) {
                if (blocked.status() != PedestrianRouteResult.Status.NO_PATH) throw blocked;
                result = new PedestrianRouteResult(blocked.status(), List.of(), 0, 0, blocked.getMessage());
            }
            results.put(target, result);
            if (result.status() == PedestrianRouteResult.Status.FOUND)
                accepted.put(target, result.route().size() > 1 ? Optional.of(new PedestrianRouteReceipt(result.route())) : Optional.empty());
        }
        return result.status() == PedestrianRouteResult.Status.FOUND;
    }
    /** Only call when no usable result was selected. Unknown calculation is not a negative access proof. */
    public void awaitUnresolved() {
        PedestrianRouteResult first = null;
        for (var entry : results.entrySet()) {
            var result = entry.getValue();
            if (result.status() == PedestrianRouteResult.Status.FOUND || result.status() == PedestrianRouteResult.Status.NO_PATH) continue;
            if (first == null) first = result;
            if (result.awaitable())
                PedestrianRoutePlanning.await(PedestrianRouteRequest.of(geometry, origin, entry.getKey()));
        }
        if (first != null) throw new KnownPedestrianNavigation.RouteUnavailable(first.status(), first.reason());
    }
    public PedestrianAccessReceipt receipt(Set<SurfaceAnchor> selected) {
        return PedestrianAccessReceipt.selected(origin, accepted, selected);
    }
}
