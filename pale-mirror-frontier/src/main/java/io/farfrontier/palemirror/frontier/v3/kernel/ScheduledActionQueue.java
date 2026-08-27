package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;

/** Engine-owned schedule index. Its only mutation path is explicit schedule or cancellation events. */
public final class ScheduledActionQueue {
    private final NavigableSet<ScheduledAction> ordered = new TreeSet<>();
    private final Map<ScheduleId, ScheduledAction> byId = new HashMap<>();

    public void schedule(ScheduledAction action) {
        Objects.requireNonNull(action, "action");
        if (byId.putIfAbsent(action.id(), action) != null) {
            throw new IllegalArgumentException("scheduled action already exists: " + action.id().value());
        }
        if (!ordered.add(action)) {
            byId.remove(action.id());
            throw new IllegalStateException("scheduled action ordering collision: " + action.id().value());
        }
    }

    public boolean cancel(ScheduleId id) {
        ScheduledAction action = byId.remove(Objects.requireNonNull(id, "schedule id"));
        return action != null && ordered.remove(action);
    }

    public ScheduledActionQueue copy() {
        ScheduledActionQueue copy = new ScheduledActionQueue();
        snapshot().forEach(copy::schedule);
        return copy;
    }

    public boolean isHead(ScheduledAction action) {
        return action.equals(ordered.isEmpty() ? null : ordered.first());
    }

    /**
     * Selects work without consuming it. The engine acknowledges each action only after its
     * corresponding transaction is committed, so a failing reducer cannot lose future work.
     */
    public ScheduledWork selectDue(SimInstant instant, WorkBudget budget) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(budget, "budget");
        List<ScheduledAction> executed = new ArrayList<>();
        int weight = 0;
        for (ScheduledAction next : ordered) {
            if (next.dueAt().compareTo(instant) > 0) {
                break;
            }
            if (executed.size() == budget.maxActions() || next.weight() > budget.maxWeight() - weight) {
                return new ScheduledWork(executed, next);
            }
            executed.add(next);
            weight = Math.addExact(weight, next.weight());
        }
        return new ScheduledWork(executed, null);
    }

    /** Acknowledges the current head after its immutable completion event has committed. */
    public void acknowledge(ScheduledAction action) {
        Objects.requireNonNull(action, "action");
        ScheduledAction current = ordered.isEmpty() ? null : ordered.first();
        if (!action.equals(current)) {
            throw new IllegalStateException("scheduled action acknowledgement is not the queue head: " + action.id().value());
        }
        ordered.pollFirst();
        byId.remove(action.id());
    }

    public List<ScheduledAction> snapshot() {
        return List.copyOf(ordered);
    }

    public int size() {
        return ordered.size();
    }
}
