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

    public ScheduledWork takeDue(SimInstant instant, WorkBudget budget) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(budget, "budget");
        List<ScheduledAction> executed = new ArrayList<>();
        int weight = 0;
        while (!ordered.isEmpty()) {
            ScheduledAction next = ordered.first();
            if (next.dueAt().compareTo(instant) > 0) {
                break;
            }
            if (executed.size() == budget.maxActions() || next.weight() > budget.maxWeight() - weight) {
                return new ScheduledWork(executed, true);
            }
            ordered.pollFirst();
            byId.remove(next.id());
            executed.add(next);
            weight = Math.addExact(weight, next.weight());
        }
        return new ScheduledWork(executed, false);
    }

    public List<ScheduledAction> snapshot() {
        return List.copyOf(ordered);
    }

    public int size() {
        return ordered.size();
    }
}
