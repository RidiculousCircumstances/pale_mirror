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

    public void replace(ScheduledAction action, List<PedestrianRouteRequest> requests) {
        remove(action.id());
        if (requests.isEmpty()) return;
        var distinct = List.copyOf(new LinkedHashSet<>(requests));
        waits.put(action.id(), new Wait(action, distinct));
        for (var request : distinct) dependents.computeIfAbsent(request, ignored -> new LinkedHashSet<>()).add(action.id());
    }
    public void remove(ScheduleId id) {
        ready.remove(id);
        var previous = waits.remove(id);
        if (previous == null) return;
        for (var request : previous.requests()) {
            var owners = dependents.get(request); owners.remove(id);
            if (owners.isEmpty()) dependents.remove(request);
        }
    }
    public void changed(List<PedestrianPlanningChange> changes) {
        for (var change : changes) ready.addAll(dependents.getOrDefault(change.request(), Set.of()));
    }
    /** Exact schedule equality is the generation fence; stale/retired/due owners never receive a command. */
    public List<ScheduledAction> ready(FrontierScheduleView view) {
        var retained = new HashMap<ScheduleId, ScheduledAction>();
        for (var action : view.schedules()) retained.put(action.id(), action);
        for (var entry : List.copyOf(waits.entrySet())) {
            var action = entry.getValue().action();
            if (!action.equals(retained.get(entry.getKey()))) remove(entry.getKey());
            else if (ready.contains(entry.getKey())) {
                if (action.dueAt().ticks() <= view.instant().ticks() || action.dueAt().ticks() - view.instant().ticks() == 1L)
                    remove(entry.getKey());
            }
        }
        // Completion order, not original registration order: a repeatedly rearmed owner
        // joins the tail and cannot starve another retained ready continuation.
        return ready.stream().map(id -> waits.get(id).action()).toList();
    }

    public int waitingCount() { return waits.size(); }
    public int readyCount() { return ready.size(); }
}
