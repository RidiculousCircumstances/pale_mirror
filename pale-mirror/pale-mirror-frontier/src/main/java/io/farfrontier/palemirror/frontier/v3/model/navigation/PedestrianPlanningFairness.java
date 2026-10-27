package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;

/** Bounded, work-conserving admission and weighted rotation; no resident or job knowledge. */
final class PedestrianPlanningFairness {
    enum Lane { CLEARANCE, LOCAL, DISTANT }
    private static final List<Lane> ROTATION = List.of(Lane.CLEARANCE, Lane.LOCAL, Lane.LOCAL, Lane.DISTANT,
            Lane.CLEARANCE, Lane.LOCAL, Lane.LOCAL, Lane.DISTANT);
    private final Map<PedestrianRouteRequest, Lane> lanes = new HashMap<>();
    private final Map<PedestrianRouteRequest, Long> waitingOrder = new HashMap<>();
    private final EnumMap<Lane, NavigableMap<Long, PedestrianRouteRequest>> waiting = new EnumMap<>(Lane.class);
    private long nextOrder;
    private int cursor;
    PedestrianPlanningFairness() { for (var lane : Lane.values()) waiting.put(lane, new TreeMap<>()); }
    record Address(Lane lane, Long waitingOrder) { }
    Address capture(PedestrianRouteRequest request) { return new Address(lane(request), waitingOrder.get(request)); }

    void requested(PedestrianRouteRequest request, PedestrianPlanningService service) {
        var lane = service == PedestrianPlanningService.PASSAGE_CLEARANCE ? Lane.CLEARANCE
                : PedestrianLocalSearch.nearby(request.start(), request.target()) ? Lane.LOCAL : Lane.DISTANT;
        var old = lanes.get(request);
        if (old != null && old.compareTo(lane) <= 0) return;
        var order = waitingOrder.get(request);
        if (order != null) waiting.get(old).remove(order);
        lanes.put(request, lane);
        if (order != null) waiting.get(lane).put(order, request);
    }
    Lane lane(PedestrianRouteRequest request) { return Objects.requireNonNull(lanes.get(request)); }
    private int quota(Lane lane) { return lane == Lane.LOCAL ? 4 : 2; }
    private long count(Collection<PedestrianRouteRequest> active, Lane lane) {
        return active.stream().filter(request -> lane(request) == lane).count();
    }
    void defer(PedestrianRouteRequest request) {
        if (waitingOrder.containsKey(request)) return;
        nextOrder = Math.incrementExact(nextOrder);
        waitingOrder.put(request, nextOrder); waiting.get(lane(request)).put(nextOrder, request);
    }
    void admit(PedestrianRouteRequest request) {
        var order = waitingOrder.remove(request);
        if (order != null) waiting.get(lane(request)).remove(order);
    }
    PedestrianRouteRequest nextAdmission(Collection<PedestrianRouteRequest> active) {
        // Each service head is indexed. Re-reading a pending calculation must
        // not scan every deferred demand, especially for an empty priority lane.
        for (var lane : Lane.values()) if (count(active, lane) < quota(lane) && !waiting.get(lane).isEmpty())
            return waiting.get(lane).firstEntry().getValue();
        Map.Entry<Long, PedestrianRouteRequest> oldest = null;
        for (var lane : Lane.values()) if (!waiting.get(lane).isEmpty()) {
            var head = waiting.get(lane).firstEntry();
            if (oldest == null || head.getKey() < oldest.getKey()) oldest = head;
        }
        return oldest == null ? null : oldest.getValue();
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
    void restore(PedestrianRouteRequest request, Address address) {
        lanes.put(request, Objects.requireNonNull(address.lane()));
        if (address.waitingOrder() != null) {
            waitingOrder.put(request, address.waitingOrder());
            waiting.get(address.lane()).put(address.waitingOrder(), request);
        }
    }
    void remove(PedestrianRouteRequest request) { admit(request); lanes.remove(request); }
    void clear() { lanes.clear(); waitingOrder.clear(); waiting.values().forEach(Map::clear); nextOrder = 0; cursor = 0; }
}
