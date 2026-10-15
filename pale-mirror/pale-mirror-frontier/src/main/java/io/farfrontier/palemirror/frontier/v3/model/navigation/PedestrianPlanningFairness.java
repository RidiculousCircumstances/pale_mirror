package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;

/** Bounded, work-conserving admission and weighted rotation; no resident or job knowledge. */
final class PedestrianPlanningFairness {
    enum Lane { CLEARANCE, LOCAL, DISTANT }
    private static final List<Lane> ROTATION = List.of(Lane.CLEARANCE, Lane.LOCAL, Lane.LOCAL, Lane.DISTANT,
            Lane.CLEARANCE, Lane.LOCAL, Lane.LOCAL, Lane.DISTANT);
    private final Map<PedestrianRouteRequest, Lane> lanes = new HashMap<>();
    private int cursor;

    void requested(PedestrianRouteRequest request, PedestrianPlanningService service) {
        var lane = service == PedestrianPlanningService.PASSAGE_CLEARANCE ? Lane.CLEARANCE
                : PedestrianLocalSearch.nearby(request.start(), request.target()) ? Lane.LOCAL : Lane.DISTANT;
        lanes.merge(request, lane, (old, next) -> old.compareTo(next) <= 0 ? old : next);
    }
    Lane lane(PedestrianRouteRequest request) { return Objects.requireNonNull(lanes.get(request)); }
    private int quota(Lane lane) { return lane == Lane.LOCAL ? 4 : 2; }
    private long count(Collection<PedestrianRouteRequest> active, Lane lane) {
        return active.stream().filter(request -> lane(request) == lane).count();
    }
    PedestrianRouteRequest nextAdmission(Collection<PedestrianRouteRequest> waiting, Collection<PedestrianRouteRequest> active) {
        for (var lane : Lane.values()) if (count(active, lane) < quota(lane))
            for (var request : waiting) if (lane(request) == lane) return request;
        return waiting.stream().findFirst().orElse(null);
    }
    PedestrianRouteRequest yieldFor(PedestrianRouteRequest demand, Collection<PedestrianRouteRequest> active,
                                     Collection<PedestrianRouteRequest> ready) {
        var wanted = lane(demand);
        if (count(active, wanted) >= quota(wanted)) return null;
        // Only a slice boundary may yield. Preserve the suspended search, its age and all dependencies.
        for (var request : ready) if (lane(request) != wanted && count(active, lane(request)) > quota(lane(request)))
            return request;
        return null;
    }
    PedestrianRouteRequest take(Deque<PedestrianRouteRequest> ready) {
        for (int i = 0; i < ROTATION.size(); i++) {
            var lane = ROTATION.get(cursor); cursor = (cursor + 1) % ROTATION.size();
            for (var iterator = ready.iterator(); iterator.hasNext();) {
                var request = iterator.next();
                if (lane(request) == lane) { iterator.remove(); return request; }
            }
        }
        throw new IllegalStateException("nonempty planning rotation lost its declared lane");
    }
    void restore(PedestrianRouteRequest request, Lane lane) { lanes.put(request, Objects.requireNonNull(lane)); }
    void remove(PedestrianRouteRequest request) { lanes.remove(request); }
    void clear() { lanes.clear(); cursor = 0; }
}
