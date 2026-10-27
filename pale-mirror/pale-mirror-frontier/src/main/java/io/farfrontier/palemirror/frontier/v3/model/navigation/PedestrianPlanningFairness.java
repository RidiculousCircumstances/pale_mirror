package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;

/** Bounded, work-conserving admission and weighted rotation; no resident or job knowledge. */
final class PedestrianPlanningFairness {
    enum Lane { CLEARANCE, LOCAL, DISTANT }
    private static final List<Lane> ROTATION = List.of(Lane.CLEARANCE, Lane.LOCAL, Lane.LOCAL, Lane.DISTANT,
            Lane.CLEARANCE, Lane.LOCAL, Lane.LOCAL, Lane.DISTANT);
    private final Map<PedestrianRouteRequest, Lane> lanes = new HashMap<>();
    private final Map<PedestrianRouteRequest, Long> arrivalOrder = new HashMap<>();
    private final Map<PedestrianRouteRequest, Long> waitingOrder = new HashMap<>();
    private final EnumMap<Lane, NavigableMap<Long, PedestrianRouteRequest>> waiting = new EnumMap<>(Lane.class);
    private long nextOrder;
    private int cursor;
    PedestrianPlanningFairness() { for (var lane : Lane.values()) waiting.put(lane, new TreeMap<>()); }
    record Address(Lane lane, Long waitingOrder, long arrivalOrder) { }
    Address capture(PedestrianRouteRequest request) {
        return new Address(lane(request), waitingOrder.get(request), Objects.requireNonNull(arrivalOrder.get(request)));
    }

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
        // A yielded calculation is still the original arrival. Putting it behind
        // unstarted demand discards its progress when the bounded suspension
        // window fills under a stream of short, higher-priority requests.
        long order = arrivalOrder.computeIfAbsent(request, ignored -> nextOrder = Math.incrementExact(nextOrder));
        waitingOrder.put(request, order); waiting.get(lane(request)).put(order, request);
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
        // Only borrowed slots yield, and only at a slice boundary. Protect each
        // lane's oldest quota even when its other searches are currently flying;
        // otherwise repeated reclamation can suspend every long calculation.
        PedestrianRouteRequest victim = null;
        for (var request : ready) if (lane(request) != wanted) {
            long order = arrivalOrder.get(request);
            long older = active.stream().filter(value -> lane(value) == lane(request) && arrivalOrder.get(value) < order).count();
            if (older >= quota(lane(request)) && (victim == null || order > arrivalOrder.get(victim))) victim = request;
        }
        return victim;
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
        arrivalOrder.put(request, address.arrivalOrder());
        if (address.waitingOrder() != null) {
            waitingOrder.put(request, address.waitingOrder());
            waiting.get(address.lane()).put(address.waitingOrder(), request);
        }
    }
    void remove(PedestrianRouteRequest request) { admit(request); lanes.remove(request); arrivalOrder.remove(request); }
    void clear() { lanes.clear(); arrivalOrder.clear(); waitingOrder.clear(); waiting.values().forEach(Map::clear); nextOrder = 0; cursor = 0; }
}
