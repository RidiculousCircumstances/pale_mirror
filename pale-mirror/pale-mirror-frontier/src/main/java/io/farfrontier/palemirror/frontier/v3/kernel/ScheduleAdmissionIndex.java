package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Bounded engine-local read index; canonical actions/order remain queue-owned.
 * Unknown/stale predecessors consume calculation budget and fence successors.
 * An unchanged owner proof needs no business evaluation or wake-key rebuild. */
final class ScheduleAdmissionIndex<S> {
    enum Decision { READY, HELD, DEFERRED }
    private final ScheduledActionPlanner<S> planner;
    private final int capacity;
    private final Map<io.farfrontier.palemirror.frontier.v3.api.ScheduleId, ScheduledHold<S>> holds = new HashMap<>();
    private record AssessedHold<S>(ScheduledAction action, S state) { }
    /** One allocation/commit pass, not reusable knowledge across host turns. */
    private final Map<io.farfrontier.palemirror.frontier.v3.api.ScheduleId, AssessedHold<S>> passHolds = new HashMap<>();
    private int remaining;
    private ScheduledAction deferred;
    ScheduleAdmissionIndex(ScheduledActionPlanner<S> planner, int capacity) {
        this.planner = java.util.Objects.requireNonNull(planner);
        if (capacity < 1) throw new IllegalArgumentException("invalid admission index capacity");
        this.capacity = capacity;
    }
    void begin(WorkBudget budget) {
        deferred = null;
        passHolds.clear();
        // One selection and one commit check per ordinary admitted action fit;
        // rejected business evaluations are bounded too, unlike the old guard.
        remaining = budget.maxAdmissionEvaluations();
    }
    Decision assess(S state, ScheduledAction action) {
        var assessed = passHolds.get(action.id());
        if (assessed != null && assessed.action().equals(action) && assessed.state() == state) return Decision.HELD;
        var retained = holds.get(action.id());
        if (retained != null && retained.action().equals(action) && retained.stillHeld().test(state)) return Decision.HELD;
        holds.remove(action.id());
        if (remaining == 0) { if (deferred == null) deferred = action; return Decision.DEFERRED; }
        remaining--;
        if (!planner.held(state, action)) return Decision.READY;
        // Pure refusal was already checked in this exact pass/state. Commit
        // reconciliation must not charge the same held prefix over and over.
        // A new aggregate or schedule generation requires a fresh assessment;
        // a new host turn still requires its owner proof or ordinary reevaluation.
        if (passHolds.size() >= capacity) passHolds.clear();
        passHolds.put(action.id(), new AssessedHold<>(action, state));
        planner.holdProof(state, action).ifPresent(proof -> {
            if (!action.equals(proof.action()) || !proof.stillHeld().test(state))
                throw new IllegalArgumentException("scheduled hold proof is stale or addresses another action");
            if (holds.size() >= capacity) holds.clear();
            holds.put(action.id(), proof);
        });
        return Decision.HELD;
    }
    java.util.Optional<ScheduledAction> deferredAction() { return java.util.Optional.ofNullable(deferred); }
    void forget(ScheduleEffect effect) {
        var id = switch (effect) {
            case ScheduleEffect.Cancelled cancelled -> cancelled.scheduleId();
            case ScheduleEffect.Consumed consumed -> consumed.scheduleId();
            case ScheduleEffect.Rescheduled rescheduled -> rescheduled.scheduleId();
            default -> null;
        };
        if (id != null) { holds.remove(id); passHolds.remove(id); }
    }
    Set<SubjectId> wakeKeys(S state, ScheduledAction action) {
        var retained = holds.get(action.id());
        return retained != null && retained.action().equals(action) && retained.stillHeld().test(state)
                ? retained.wakeKeys() : planner.holdWakeKeys(state, action);
    }
}
