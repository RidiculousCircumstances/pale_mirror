package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Bounded engine-local read index; canonical actions/order remain queue-owned.
 * Classification cannot consume the independent READY validation allowance.
 * An unchanged owner proof needs no business evaluation or wake-key rebuild. */
final class ScheduleAdmissionIndex<S> {
    enum Decision { READY, HELD, DEFERRED }
    private final ScheduledActionPlanner<S> planner;
    private final int capacity;
    private final FrontierExecutionMetrics metrics;
    private final Map<io.farfrontier.palemirror.frontier.v3.api.ScheduleId, ScheduledHold<S>> holds = new HashMap<>();
    private static final class AssessedHold<S> {
        final ScheduledAction action;
        final S state;
        Set<SubjectId> keys;
        AssessedHold(ScheduledAction action, S state, Set<SubjectId> keys) { this.action = action; this.state = state; this.keys = keys; }
    }
    /** One allocation/commit pass, not reusable knowledge across host turns. */
    private final Map<io.farfrontier.palemirror.frontier.v3.api.ScheduleId, AssessedHold<S>> passHolds = new HashMap<>();
    private int remaining;
    private int executionRemaining;
    private ScheduledAction deferred;
    ScheduleAdmissionIndex(ScheduledActionPlanner<S> planner, int capacity) {
        this(planner, capacity, FrontierExecutionMetrics.noOp());
    }
    ScheduleAdmissionIndex(ScheduledActionPlanner<S> planner, int capacity, FrontierExecutionMetrics metrics) {
        this.metrics = java.util.Objects.requireNonNull(metrics);
        this.planner = java.util.Objects.requireNonNull(planner);
        if (capacity < 1) throw new IllegalArgumentException("invalid admission index capacity");
        this.capacity = capacity;
    }
    void begin(WorkBudget budget) {
        deferred = null;
        passHolds.clear();
        remaining = budget.maxClassificationEvaluations();
        executionRemaining = budget.maxExecutionEvaluations();
    }
    int remainingChecks() { return remaining; }
    int remainingExecutionChecks() { return executionRemaining; }
    Decision assess(S state, ScheduledAction action) {
        if (remaining == 0) { if (deferred == null) deferred = action; return Decision.DEFERRED; }
        remaining--;
        return evaluate(state, action);
    }
    Decision recheckSelected(S state, ScheduledAction action) {
        if (executionRemaining == 0) { deferred = action; return Decision.DEFERRED; }
        executionRemaining--;
        return evaluate(state, action);
    }
    private Decision evaluate(S state, ScheduledAction action) {
        var assessed = passHolds.get(action.id());
        if (assessed != null && assessed.action.equals(action) && assessed.state == state) return Decision.HELD;
        var retained = holds.get(action.id());
        if (retained != null && retained.action().equals(action) && stillHeld(retained, state)) {
            remember(state, action, retained.wakeKeys()); return Decision.HELD;
        }
        holds.remove(action.id());
        try (var ignored = detail("held", action)) {
            if (!planner.held(state, action)) return Decision.READY;
        }
        // Pure refusal was already checked in this exact pass/state. Commit
        // reconciliation must not charge the same held prefix over and over.
        // A new aggregate or schedule generation requires a fresh assessment;
        // a new host turn still requires its owner proof or ordinary reevaluation.
        remember(state, action, null);
        try (var ignored = detail("proof", action)) {
            planner.holdProof(state, action).ifPresent(proof -> {
                if (!action.equals(proof.action()) || !proof.stillHeld().test(state))
                    throw new IllegalArgumentException("scheduled hold proof is stale or addresses another action");
                if (holds.size() >= capacity) holds.clear();
                holds.put(action.id(), proof);
                passHolds.get(action.id()).keys = proof.wakeKeys();
            });
        }
        return Decision.HELD;
    }
    java.util.Optional<ScheduledAction> deferredAction() { return java.util.Optional.ofNullable(deferred); }
    void reconsider(ScheduledAction action) {
        holds.remove(action.id()); passHolds.remove(action.id());
    }
    void forget(ScheduleEffect effect) {
        var id = switch (effect) {
            case ScheduleEffect.Cancelled cancelled -> cancelled.scheduleId();
            case ScheduleEffect.Consumed consumed -> consumed.scheduleId();
            case ScheduleEffect.Rescheduled rescheduled -> rescheduled.scheduleId();
            default -> null;
        };
        if (id != null) { holds.remove(id); passHolds.remove(id); }
    }
    private void remember(S state, ScheduledAction action, Set<SubjectId> keys) {
        if (passHolds.size() >= capacity) passHolds.clear();
        passHolds.put(action.id(), new AssessedHold<>(action, state, keys));
    }
    Set<SubjectId> wakeKeys(S state, ScheduledAction action) {
        var assessed = passHolds.get(action.id());
        if (assessed != null && assessed.action.equals(action) && assessed.state == state) {
            if (assessed.keys == null) assessed.keys = Set.copyOf(freshWakeKeys(state, action));
            return assessed.keys;
        }
        var retained = holds.get(action.id());
        return retained != null && retained.action().equals(action) && stillHeld(retained, state)
                ? retained.wakeKeys() : freshWakeKeys(state, action);
    }
    private Set<SubjectId> freshWakeKeys(S state, ScheduledAction action) {
        try (var ignored = detail("wake-keys", action)) { return planner.holdWakeKeys(state, action); }
    }
    private boolean stillHeld(ScheduledHold<S> hold, S state) {
        try (var ignored = detail("retained-proof", hold.action())) { return hold.stillHeld().test(state); }
    }
    private FrontierExecutionMetrics.Span detail(String phase, ScheduledAction action) {
        if (!FrontierExecutionMetrics.safelyDetailsEnabled(metrics)) return FrontierExecutionMetrics.noOp().detail(
                FrontierExecutionMetrics.Stage.SCHEDULE_ALLOCATION, phase, action.subject().value());
        return FrontierExecutionMetrics.safelyDetail(metrics, FrontierExecutionMetrics.Stage.SCHEDULE_ALLOCATION,
                "admission." + phase + "/" + action.kind(), action.subject().value());
    }

}
