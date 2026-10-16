package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPlanningChange;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteRequest;
import java.util.*;

/** Derived runtime index. One wake per exact retained continuation, irrespective of repeated calculation signals. */
public final class PedestrianPlanningWakeIndex {
    private record Wait(ScheduledAction action, List<PedestrianRouteRequest> requests) { }
    private final Map<ScheduleId, Wait> waits = new LinkedHashMap<>();
    private final Map<PedestrianRouteRequest, Set<ScheduleId>> dependents = new LinkedHashMap<>();
    private final Set<ScheduleId> ready = new LinkedHashSet<>();
    private final Set<ScheduleId> parked = new HashSet<>();
    private final Set<PedestrianRouteRequest> released = new LinkedHashSet<>();

    public void replace(ScheduledAction action, List<PedestrianRouteRequest> requests) {
        remove(action.id());
        if (requests.isEmpty()) return;
        var distinct = List.copyOf(new LinkedHashSet<>(requests));
        waits.put(action.id(), new Wait(action, distinct));
        for (var request : distinct) dependents.computeIfAbsent(request, ignored -> new LinkedHashSet<>()).add(action.id());
    }
    public void remove(ScheduleId id) {
        ready.remove(id);
        parked.remove(id);
        var previous = waits.remove(id);
        if (previous == null) return;
        for (var request : previous.requests()) {
            var owners = dependents.get(request); owners.remove(id);
            if (owners.isEmpty()) { dependents.remove(request); released.add(request); }
        }
    }
    /** Replacements are atomic at the process boundary: a refreshed/shared demand survives. */
    public void releaseUnused(java.util.function.Consumer<PedestrianRouteRequest> release) {
        for (var request : released) if (dependents.keySet().stream().noneMatch(request::sameDemand)) release.accept(request);
        released.clear();
    }
    public void changed(List<PedestrianPlanningChange> changes) {
        for (var change : changes) ready.addAll(dependents.getOrDefault(change.request(), Set.of()));
    }
    public boolean waiting(ScheduledAction action) {
        var wait = waits.get(action.id());
        return wait != null && wait.action().equals(action) && !ready.contains(action.id());
    }
    /** Records the kernel's hold-dependency inquiry, not a domain grant or queue mutation. */
    public void markParked(ScheduledAction action) {
        if (!waiting(action)) throw new IllegalArgumentException("calculation hold has no exact unresolved continuation");
        parked.add(action.id());
    }
    /** Exact schedule equality is the generation fence; a due but parked owner still needs its addressed wake. */
    public List<ScheduledAction> ready(FrontierScheduleView view) {
        if (waits.isEmpty()) return List.of();
        var retained = new HashMap<ScheduleId, ScheduledAction>();
        for (var action : view.schedules()) retained.put(action.id(), action);
        return ready(view.instant(), action -> action.equals(retained.get(action.id())));
    }

    /** Production uses the owning kernel's exact ID lookup, not a future-queue snapshot. */
    public List<ScheduledAction> ready(io.farfrontier.palemirror.frontier.v3.api.SimInstant instant,
                                       java.util.function.Predicate<ScheduledAction> retained) {
        if (waits.isEmpty()) return List.of();
        for (var entry : List.copyOf(waits.entrySet())) {
            var action = entry.getValue().action();
            if (!retained.test(action)) remove(entry.getKey());
            else if (ready.contains(entry.getKey()) && !parked.contains(entry.getKey())
                    && (action.dueAt().ticks() <= instant.ticks()
                        || action.dueAt().ticks() - instant.ticks() == 1L)) remove(entry.getKey());
        }
        // Completion order, not original registration order: a repeatedly rearmed owner
        // joins the tail and cannot starve another retained ready continuation.
        return ready.stream().map(id -> waits.get(id).action()).toList();
    }

    public int waitingCount() { return waits.size(); }
    public int readyCount() { return ready.size(); }
}
