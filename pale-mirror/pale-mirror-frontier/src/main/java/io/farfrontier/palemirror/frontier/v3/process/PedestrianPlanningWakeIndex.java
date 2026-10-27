package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPlanningChange;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianDemandKey;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteRequest;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianCalculationMemory;
import java.util.*;

/** Derived runtime index. One wake per exact retained continuation, irrespective of repeated calculation signals. */
public final class PedestrianPlanningWakeIndex {
    private enum Origin { LIVE_CONTINUATION, RECOVERY_PREVIEW }
    private record Wait(ScheduledAction action, List<PedestrianRouteRequest> requests, Origin origin,
                        Optional<ScheduledAction> predecessor, PedestrianCalculationMemory reads) { }
    private final Map<ScheduleId, Wait> waits = new LinkedHashMap<>();
    private final Map<PedestrianDemandKey, Set<ScheduleId>> dependents = new LinkedHashMap<>();
    private final Set<ScheduleId> ready = new LinkedHashSet<>();
    private final Set<ScheduleId> parked = new HashSet<>();
    private final Set<ScheduleId> runnable = new HashSet<>();
    private final Set<ScheduleId> ownerHeld = new HashSet<>();
    private final Set<PedestrianRouteRequest> released = new LinkedHashSet<>();
    private record Retention(long from, long until, List<PedestrianRouteRequest> requests, Set<PedestrianDemandKey> keys) { }
    private Retention retention;

    public void replace(ScheduledAction action, List<PedestrianRouteRequest> requests) {
        replace(action, requests, Origin.LIVE_CONTINUATION, Optional.empty());
    }
    void restore(ScheduledAction action, List<PedestrianRouteRequest> requests) {
        replace(action, requests, Origin.RECOVERY_PREVIEW, Optional.empty());
    }
    /** A producer may have computed its successor before the WAL publishes it.
     * Retain delivery while its exact predecessor is still canonical, but never
     * expose that unpublished generation as runnable work. */
    void stage(ScheduledAction predecessor, ScheduledAction successor, List<PedestrianRouteRequest> requests) {
        if (!predecessor.id().equals(successor.id())) throw new IllegalArgumentException("planning successor changed its schedule identity");
        stage(predecessor, successor, requests, PedestrianCalculationMemory.empty());
    }
    void stage(ScheduledAction predecessor, ScheduledAction successor, List<PedestrianRouteRequest> requests,
               PedestrianCalculationMemory reads) {
        if (!predecessor.id().equals(successor.id())) throw new IllegalArgumentException("planning successor changed its schedule identity");
        replace(successor, requests, Origin.LIVE_CONTINUATION, Optional.of(predecessor), reads);
    }
    PedestrianCalculationMemory reads(ScheduledAction action) {
        var wait = waits.get(action.id());
        return wait != null && wait.action().equals(action) ? wait.reads() : PedestrianCalculationMemory.empty();
    }
    private void replace(ScheduledAction action, List<PedestrianRouteRequest> requests, Origin origin,
                         Optional<ScheduledAction> predecessor) {
        replace(action, requests, origin, predecessor, PedestrianCalculationMemory.empty());
    }
    private void replace(ScheduledAction action, List<PedestrianRouteRequest> requests, Origin origin,
                         Optional<ScheduledAction> predecessor, PedestrianCalculationMemory reads) {
        remove(action.id());
        if (requests.isEmpty()) return;
        var demands = new LinkedHashMap<PedestrianDemandKey, PedestrianRouteRequest>();
        requests.forEach(request -> demands.put(PedestrianDemandKey.of(request), request));
        var distinct = List.copyOf(demands.values());
        waits.put(action.id(), new Wait(action, distinct, origin, predecessor, reads)); retention = null;
        for (var request : distinct) dependents.computeIfAbsent(PedestrianDemandKey.of(request), ignored -> new LinkedHashSet<>()).add(action.id());
    }
    public void remove(ScheduleId id) {
        ready.remove(id);
        parked.remove(id);
        runnable.remove(id); ownerHeld.remove(id);
        var previous = waits.remove(id);
        if (previous == null) return;
        retention = null;
        for (var request : previous.requests()) {
            var key = PedestrianDemandKey.of(request);
            var owners = dependents.get(key); owners.remove(id);
            if (owners.isEmpty()) { dependents.remove(key); released.add(request); }
        }
    }
    /** Live continuations retain delivery even across a future retry deadline.
     * Detached future previews are optional cache users until due/runnable.
     * An explicit business-owner refusal releases delivery pressure. */
    public List<PedestrianRouteRequest> retainedRequests(SimInstant instant) { return retention(instant).requests(); }
    public Set<PedestrianDemandKey> retainedDemandKeys(SimInstant instant) { return retention(instant).keys(); }
    private Retention retention(SimInstant instant) {
        long now = instant.ticks();
        if (retention != null && now >= retention.from() && now < retention.until()) return retention;
        var requests = new LinkedHashMap<PedestrianDemandKey, PedestrianRouteRequest>();
        long until = Long.MAX_VALUE;
        for (var entry : waits.entrySet()) if (!ownerHeld.contains(entry.getKey())) {
            var wait = entry.getValue();
            if (wait.origin() == Origin.LIVE_CONTINUATION || wait.action().dueAt().ticks() <= now || runnable.contains(entry.getKey()))
                wait.requests().stream().filter(request -> !wait.reads().holdsReadFor(request))
                        .forEach(request -> requests.put(PedestrianDemandKey.of(request), request));
            else until = Math.min(until, wait.action().dueAt().ticks());
        }
        retention = new Retention(now, until, List.copyOf(requests.values()), Set.copyOf(requests.keySet()));
        return retention;
    }
    public void ownerHeld(ScheduledAction action, boolean held) {
        var wait = waits.get(action.id());
        if (wait == null || !wait.action().equals(action)) return;
        if (held ? ownerHeld.add(action.id()) : ownerHeld.remove(action.id())) retention = null;
    }
    public void releaseUnused(java.util.function.Consumer<PedestrianRouteRequest> release) {
        var retired = new HashSet<PedestrianDemandKey>();
        for (var request : released) {
            var key = PedestrianDemandKey.of(request);
            if (!dependents.containsKey(key) && retired.add(key)) release.accept(request);
        }
        released.clear();
    }
    public void changed(List<PedestrianPlanningChange> changes) { changed(changes, ignored -> Optional.empty()); }
    /** Acknowledge data delivery without running or accelerating the waiting business action. */
    public void changed(List<PedestrianPlanningChange> changes,
            java.util.function.Function<PedestrianRouteRequest, Optional<PedestrianCalculationMemory.Read>> completedRead) {
        for (var change : changes) {
            var read = change.kind() == PedestrianPlanningChange.Kind.RESULT_AVAILABLE ? completedRead.apply(change.request())
                    : Optional.<PedestrianCalculationMemory.Read>empty();
            for (var id : dependents.getOrDefault(PedestrianDemandKey.of(change.request()), Set.of())) {
                if (read.isPresent()) {
                    var wait = waits.get(id);
                    waits.put(id, new Wait(wait.action(), wait.requests(), wait.origin(), wait.predecessor(), wait.reads().including(read.orElseThrow())));
                    retention = null;
                }
                if (!runnable.contains(id)) ready.add(id);
            }
        }
    }
    /** An accepted wake transfers demand to the exact runnable generation. It does
     * not retire sibling calculations before the owner actually resumes. */
    public void resumed(ScheduledAction expected, ScheduledAction replacement) {
        var wait = waits.get(expected.id());
        if (wait == null || !wait.action().equals(expected) || !ready.contains(expected.id())
                || !expected.id().equals(replacement.id()) || !expected.subject().equals(replacement.subject())
                || !expected.kind().equals(replacement.kind()) || expected.priority() != replacement.priority()
                || expected.weight() != replacement.weight())
            throw new IllegalArgumentException("planning resume lost its exact continuation");
        waits.put(expected.id(), new Wait(replacement, wait.requests(), Origin.LIVE_CONTINUATION, Optional.empty(), wait.reads())); retention = null;
        makeRunnable(expected.id());
    }
    private void makeRunnable(ScheduleId id) {
        ready.remove(id); parked.remove(id); if (runnable.add(id)) retention = null;
    }
    public boolean waiting(ScheduledAction action) {
        var wait = waits.get(action.id());
        return wait != null && wait.action().equals(action) && !ready.contains(action.id()) && !runnable.contains(action.id());
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
        var unpublished = new HashSet<ScheduleId>();
        for (var entry : List.copyOf(waits.entrySet())) {
            var wait = entry.getValue(); var action = wait.action();
            if (!retained.test(action)) {
                if (wait.predecessor().filter(retained).isPresent()) unpublished.add(entry.getKey());
                else remove(entry.getKey());
            } else if (ready.contains(entry.getKey()) && !parked.contains(entry.getKey())
                    && (action.dueAt().ticks() <= instant.ticks()
                        || action.dueAt().ticks() - instant.ticks() == 1L)) makeRunnable(entry.getKey());
        }
        // Completion order, not original registration order: a repeatedly rearmed owner
        // joins the tail and cannot starve another retained ready continuation.
        return ready.stream().filter(id -> !unpublished.contains(id)).map(id -> waits.get(id).action()).toList();
    }

    /** Bounded read-only explanation of retained calculation subscriptions. */
    public record WaitDiagnostic(ScheduledAction action, List<PedestrianRouteRequest> requests,
                                 boolean parked, boolean runnable, boolean ownerHeld, String origin) { }
    public List<WaitDiagnostic> diagnostics(int maximum) {
        if (maximum < 0 || maximum > 8) throw new IllegalArgumentException("unbounded continuation diagnostics");
        return waits.values().stream().limit(maximum).map(wait -> new WaitDiagnostic(wait.action(), wait.requests(),
                parked.contains(wait.action().id()), runnable.contains(wait.action().id()), ownerHeld.contains(wait.action().id()),
                wait.origin().name())).toList();
    }
    public int waitingCount() { return waits.size(); }
    boolean tracks(ScheduledAction action) { return waits.containsKey(action.id()); }
    public int readyCount() { return ready.size(); }
}
