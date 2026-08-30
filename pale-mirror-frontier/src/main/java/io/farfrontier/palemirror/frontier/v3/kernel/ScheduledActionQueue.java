package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    public boolean isHead(ScheduledAction action) {
        return action.equals(ordered.isEmpty() ? null : ordered.first());
    }

    /**
     * Prepares one atomic schedule transition without cloning the whole future-work index.
     * The caller may commit the transition only after the matching canonical transaction is
     * durable; a rejected reducer or failed WAL append leaves this queue untouched.
     */
    public Mutation beginMutation() {
        return new Mutation(this);
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

    /** First queued due instant strictly after the caller's canonical instant. */
    public Optional<SimInstant> nextDueAfter(SimInstant instant) {
        Objects.requireNonNull(instant, "instant");
        return ordered.stream().map(ScheduledAction::dueAt).filter(value -> value.compareTo(instant) > 0).findFirst();
    }

    /** Small copy-on-write overlay for exactly one canonical transaction. */
    static final class Mutation {
        private final ScheduledActionQueue base;
        private final Map<ScheduleId, ScheduledAction> created = new LinkedHashMap<>();
        private final Set<ScheduleId> removed = new LinkedHashSet<>();
        private boolean committed;

        private Mutation(ScheduledActionQueue base) {
            this.base = Objects.requireNonNull(base, "base");
        }

        void schedule(ScheduledAction action) {
            requireOpen(); Objects.requireNonNull(action, "action");
            if (find(action.id()) != null) {
                throw new IllegalArgumentException("scheduled action already exists: " + action.id().value());
            }
            // ScheduledAction's final ordering key is its globally unique ID, so a distinct
            // ID cannot collide with an unchanged queue entry. Avoid scanning all future work.
            created.put(action.id(), action);
        }

        boolean cancel(ScheduleId id) {
            requireOpen(); Objects.requireNonNull(id, "schedule id");
            ScheduledAction action = find(id);
            if (action == null) return false;
            if (created.remove(id) == null) removed.add(id);
            return true;
        }

        ScheduledAction head() {
            ScheduledAction retained = base.ordered.stream().filter(action -> !removed.contains(action.id())).findFirst().orElse(null);
            ScheduledAction added = created.values().stream().min(ScheduledAction::compareTo).orElse(null);
            if (retained == null) return added;
            if (added == null) return retained;
            return retained.compareTo(added) <= 0 ? retained : added;
        }

        void acknowledge(ScheduledAction action) {
            requireOpen(); Objects.requireNonNull(action, "action");
            ScheduledAction current = head();
            if (!action.equals(current)) {
                throw new IllegalStateException("scheduled action acknowledgement is not the queue head: " + action.id().value());
            }
            if (created.remove(action.id()) == null) removed.add(action.id());
        }

        /** Applies a prevalidated overlay. No allocation proportional to unchanged future work occurs. */
        void commit() {
            requireOpen();
            removed.forEach(base::cancel);
            created.values().forEach(base::schedule);
            committed = true;
        }

        private ScheduledAction find(ScheduleId id) {
            ScheduledAction added = created.get(id);
            if (added != null) return added;
            if (removed.contains(id)) return null;
            return base.byId.get(id);
        }

        private void requireOpen() {
            if (committed) throw new IllegalStateException("schedule mutation is already committed");
        }
    }
}
