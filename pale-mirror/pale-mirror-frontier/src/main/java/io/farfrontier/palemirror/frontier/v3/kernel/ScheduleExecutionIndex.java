package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierAdvisoryStatus.Readiness;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Function;

/** Reconstructible readiness only. Canonical membership and generation belong to the queue. */
final class ScheduleExecutionIndex {
    private static final long HOLD_AUDIT_TICKS = 1_200L;
    private record Wait(ScheduledAction action, Set<SubjectId> keys, long auditAt) { }
    private final NavigableSet<ScheduledAction> future = new TreeSet<>();
    private final LinkedHashMap<ScheduleId, ScheduledAction> checking = new LinkedHashMap<>();
    private final NavigableSet<ScheduledAction> ready = new TreeSet<>();
    private record CalculationWait(ScheduledAction action, boolean capacity) { }
    // Technical waits have no domain wake keys or polling round. Membership remains canonical.
    private final LinkedHashMap<ScheduleId, CalculationWait> calculations = new LinkedHashMap<>();
    private final Map<ScheduleId, Wait> waiting = new HashMap<>();
    private final LinkedHashMap<ScheduleId, ScheduledAction> polled = new LinkedHashMap<>();
    private final Map<SubjectId, Set<ScheduleId>> byDependency = new HashMap<>();
    private final NavigableMap<Long, NavigableSet<ScheduledAction>> audits = new TreeMap<>();
    private final Set<ScheduleId> auditCandidates = new HashSet<>();
    private final Map<ScheduleId, Long> readySince = new HashMap<>();
    private long missedWakes;
    private Object pollInputs;
    private SimInstant pollInstant;
    private int pollRemaining;

    void created(ScheduledAction action) {
        future.add(action); readySince.put(action.id(), action.dueAt().ticks());
    }
    void removed(ScheduledAction action) {
        future.remove(action); checking.remove(action.id()); ready.remove(action);
        removeWait(action.id()); calculations.remove(action.id()); auditCandidates.remove(action.id()); readySince.remove(action.id());
    }

    /** Metadata promotion is bounded separately from owner business assessment. */
    void begin(SimInstant now, int limit, Optional<Object> inputs) {
        if (inputs.isEmpty() || inputs.orElseThrow() != pollInputs || !now.equals(pollInstant)) {
            pollInputs = inputs.orElse(null); pollInstant = now; pollRemaining = polled.size();
        }
        promoteDue(now, limit);
        for (int count = 0; count < limit && !audits.isEmpty() && audits.firstKey() <= now.ticks(); count++) {
            var action = audits.firstEntry().getValue().first();
            removeWait(action.id()); checking.put(action.id(), action);
            readySince.put(action.id(), now.ticks()); auditCandidates.add(action.id());
        }
        // No wake address was declared: inspect fairly, never invent a dependency
        // or let an unchanged refusal stand in front of independently READY work.
        // A cooperative continuation drains the captured polling round. Queue-only
        // revisions do not change the immutable owner inputs or restart that round. Refilled
        // refusals cannot renew the round indefinitely at the same state/time.
        int count = Math.min(limit, Math.min(pollRemaining, polled.size()));
        pollRemaining -= count;
        for (int index = 0; index < count; index++) {
            var action = polled.values().iterator().next();
            removeWait(action.id()); checking.put(action.id(), action);
        }
    }
    void promoteDue(SimInstant now, int limit) {
        for (int count = 0; count < limit && !future.isEmpty() && future.first().dueAt().compareTo(now) <= 0; count++) {
            var action = future.pollFirst(); checking.put(action.id(), action);
        }
    }

    void classify(SimInstant now, int limit,
                  Function<ScheduledAction, ScheduleAdmissionIndex.Decision> assessment,
                  Function<ScheduledAction, Set<SubjectId>> dependencies) {
        int count = Math.min(limit, checking.size());
        for (int index = 0; index < count; index++) {
            var entry = checking.entrySet().iterator().next();
            var action = entry.getValue();
            var decision = assessment.apply(action);
            if (decision == ScheduleAdmissionIndex.Decision.DEFERRED) return;
            checking.remove(action.id());
            if (decision == ScheduleAdmissionIndex.Decision.HELD) hold(action, dependencies.apply(action), now);
            else {
                if (auditCandidates.remove(action.id()) && missedWakes < Long.MAX_VALUE) missedWakes++;
                ready.add(action); readySince.putIfAbsent(action.id(), now.ticks());
            }
        }
    }

    void hold(ScheduledAction action, Set<SubjectId> dependencies, SimInstant now) {
        ready.remove(action); checking.remove(action.id()); removeWait(action.id());
        readySince.remove(action.id()); auditCandidates.remove(action.id());
        var keys = Set.copyOf(dependencies);
        long auditAt = now.ticks() > Long.MAX_VALUE - HOLD_AUDIT_TICKS ? Long.MAX_VALUE : now.ticks() + HOLD_AUDIT_TICKS;
        waiting.put(action.id(), new Wait(action, keys, auditAt));
        if (keys.isEmpty()) polled.put(action.id(), action);
        else {
            for (var key : keys) byDependency.computeIfAbsent(key, ignored -> new HashSet<>()).add(action.id());
            audits.computeIfAbsent(auditAt, ignored -> new TreeSet<>()).add(action);
        }
    }
    void holdCalculation(ScheduledAction action, boolean capacity) {
        ready.remove(action); checking.remove(action.id()); future.remove(action); removeWait(action.id());
        readySince.remove(action.id()); auditCandidates.remove(action.id());
        calculations.put(action.id(), new CalculationWait(action, capacity));
    }
    ScheduledAction firstCapacityWait() {
        for (var wait : calculations.values()) if (wait.capacity()) return wait.action();
        return null;
    }
    int capacityWaitCount() { return (int) calculations.values().stream().filter(CalculationWait::capacity).count(); }
    Readiness readiness(ScheduledAction action) {
        if (ready.contains(action)) return Readiness.READY;
        if (checking.containsKey(action.id())) return Readiness.CHECKING;
        if (future.contains(action)) return Readiness.FUTURE;
        if (calculations.containsKey(action.id())) return Readiness.CALCULATION;
        return waiting.containsKey(action.id()) ? Readiness.HELD
                : Readiness.ABSENT;
    }
    void releaseCalculation(ScheduledAction action, SimInstant now) {
        if (calculations.remove(action.id()) == null) return;
        if (action.dueAt().compareTo(now) > 0) future.add(action);
        else reconsider(action, now.ticks());
    }
    ScheduledAction firstReady() { return ready.isEmpty() ? null : ready.first(); }
    SimInstant readyNotBefore(ScheduledAction action) {
        if (!ready.contains(action)) throw new IllegalArgumentException("action has no ready admission");
        return new SimInstant(readySince.get(action.id()));
    }
    ScheduledAction firstChecking(SimInstant now) {
        if (!checking.isEmpty()) return checking.values().iterator().next();
        return !future.isEmpty() && future.first().dueAt().compareTo(now) <= 0 ? future.first() : null;
    }
    List<ScheduledAction> readyActions() { return List.copyOf(ready); }

    void wake(Set<SubjectId> keys, long atTick) {
        var actions = new TreeSet<ScheduledAction>();
        for (var key : keys) for (var id : byDependency.getOrDefault(key, Set.of())) actions.add(waiting.get(id).action());
        for (var action : actions) reconsider(action, atTick);
    }
    void wakeAll(long atTick) {
        var actions = new TreeSet<ScheduledAction>(); waiting.values().forEach(wait -> actions.add(wait.action()));
        for (var action : actions) reconsider(action, atTick);
    }
    void signalReadiness(ScheduledAction action, long atTick) {
        if (ready.contains(action) || checking.containsKey(action.id())) return;
        future.remove(action);
        reconsider(action, atTick);
    }
    private void reconsider(ScheduledAction action, long atTick) {
        removeWait(action.id()); calculations.remove(action.id()); checking.put(action.id(), action);
        auditCandidates.remove(action.id()); readySince.put(action.id(), Math.max(action.dueAt().ticks(), atTick));
    }
    private void removeWait(ScheduleId id) {
        var wait = waiting.remove(id); polled.remove(id);
        if (wait == null || wait.keys().isEmpty()) return;
        for (var key : wait.keys()) {
            var ids = byDependency.get(key); ids.remove(id); if (ids.isEmpty()) byDependency.remove(key);
        }
        var actions = audits.get(wait.auditAt()); actions.remove(wait.action());
        if (actions.isEmpty()) audits.remove(wait.auditAt());
    }
    int heldCount() { return waiting.size() + calculations.size(); }
    int readyCount() { return ready.size(); }
    long missedWakes() { return missedWakes; }
    long oldestReadyLag(long now) {
        long oldest = 0;
        for (var action : ready) oldest = Math.max(oldest, Math.max(0, now - readySince.get(action.id())));
        return oldest;
    }
    long oldestReadyDeadlineLag(long now) { return ready.isEmpty() ? 0 : Math.max(0, now - ready.first().dueAt().ticks()); }
    Optional<SimInstant> nextBoundary() {
        Long tick = future.isEmpty() ? null : future.first().dueAt().ticks();
        if (!checking.isEmpty()) tick = minimum(tick, checking.values().iterator().next().dueAt().ticks());
        if (!ready.isEmpty()) tick = minimum(tick, ready.first().dueAt().ticks());
        if (!polled.isEmpty()) tick = minimum(tick, polled.values().iterator().next().dueAt().ticks());
        if (!audits.isEmpty()) tick = minimum(tick, audits.firstKey());
        return tick == null ? Optional.empty() : Optional.of(new SimInstant(tick));
    }
    private static long minimum(Long value, long other) { return value == null ? other : Math.min(value, other); }
}
