package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.function.Function;
import java.util.function.Predicate;

/** Engine-owned schedule index. Its only mutation path is explicit schedule or cancellation events. */
public final class ScheduledActionQueue {
    private static final long HOLD_AUDIT_TICKS = 1_200L;
    private final NavigableSet<ScheduledAction> ordered = new TreeSet<>();
    /** Reconstructible execution index; parked actions remain in the canonical ordered queue. */
    private final NavigableSet<ScheduledAction> runnable = new TreeSet<>();
    private final Map<ScheduleId, ScheduledAction> byId = new HashMap<>();
    private final Map<ReviewKey, NavigableSet<ScheduledAction>> byOwnerKind = new HashMap<>();
    private final NavigableMap<Long, Integer> deadlines = new TreeMap<>();
    private final Map<ScheduleId, Long> readySince = new HashMap<>();
    private final Map<ScheduleId, Parked> parked = new HashMap<>();
    private final Map<SubjectId, Set<ScheduleId>> parkedByKey = new HashMap<>();
    private final NavigableMap<Long, Set<ScheduleId>> auditAt = new TreeMap<>();
    private long auditReadyWithoutWake;
    private List<ScheduledAction> immutableSnapshot;

    private record Parked(ScheduledAction action, Set<SubjectId> keys, long auditTick) { }
    private record ReviewKey(SubjectId owner, String kind) {
        static ReviewKey of(ScheduledAction action) { return new ReviewKey(action.subject(), action.kind()); }
    }

    public void schedule(ScheduledAction action) {
        Objects.requireNonNull(action, "action");
        if (byId.putIfAbsent(action.id(), action) != null) {
            throw new IllegalArgumentException("scheduled action already exists: " + action.id().value());
        }
        if (!ordered.add(action)) {
            byId.remove(action.id());
            throw new IllegalStateException("scheduled action ordering collision: " + action.id().value());
        }
        runnable.add(action);
        byOwnerKind.computeIfAbsent(ReviewKey.of(action), ignored -> new TreeSet<>()).add(action);
        deadlines.merge(action.dueAt().ticks(), 1, Integer::sum);
        readySince.put(action.id(), action.dueAt().ticks());
        immutableSnapshot = null;
    }

    public boolean cancel(ScheduleId id) {
        ScheduledAction action = byId.remove(Objects.requireNonNull(id, "schedule id"));
        if (action == null) return false;
        immutableSnapshot = null;
        runnable.remove(action);
        removeOwnerKind(action);
        removeDeadline(action);
        forgetParked(action.id());
        return ordered.remove(action);
    }

    public boolean isHead(ScheduledAction action) {
        return action.equals(ordered.isEmpty() ? null : ordered.first());
    }

    public boolean isEligibleHead(ScheduledAction action, Predicate<ScheduledAction> eligible) {
        for (ScheduledAction candidate : ordered) {
            if (eligible.test(candidate)) return action.equals(candidate);
        }
        return false;
    }

    public boolean isEligibleHead(ScheduledAction action, SimInstant instant,
                                  Predicate<ScheduledAction> eligible,
                                  Function<ScheduledAction, Set<SubjectId>> holdWakeKeys) {
        Set<ScheduleId> audited = auditExpired(instant.ticks());
        for (Iterator<ScheduledAction> iterator = runnable.iterator(); iterator.hasNext();) {
            ScheduledAction candidate = iterator.next();
            if (candidate.dueAt().compareTo(instant) > 0) return false;
            if (eligible.test(candidate)) {
                countMissedWake(audited, candidate);
                return action.equals(candidate);
            }
            Set<SubjectId> keys = holdWakeKeys.apply(candidate);
            if (!keys.isEmpty()) {
                iterator.remove();
                park(candidate, keys, instant.ticks());
            }
        }
        return false;
    }

    /** Exact immutable membership check used by an engine-owned continuation binding. */
    public boolean containsExact(ScheduledAction action) {
        Objects.requireNonNull(action, "scheduled action");
        return action.equals(byId.get(action.id()));
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
        return selectDue(instant, budget, ignored -> true);
    }

    public ScheduledWork selectDue(SimInstant instant, WorkBudget budget, Predicate<ScheduledAction> eligible) {
        return selectDue(instant, budget, eligible, ignored -> Set.of());
    }

    public ScheduledWork selectDue(SimInstant instant, WorkBudget budget, Predicate<ScheduledAction> eligible,
                                   Function<ScheduledAction, Set<SubjectId>> holdWakeKeys) {
        return selectDue(instant, budget, eligible, holdWakeKeys, ScheduledAction::weight);
    }

    public ScheduledWork selectDue(SimInstant instant, WorkBudget budget, Predicate<ScheduledAction> eligible,
                                   Function<ScheduledAction, Set<SubjectId>> holdWakeKeys,
                                   java.util.function.ToIntFunction<ScheduledAction> admissionWeight) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(eligible, "eligible");
        Objects.requireNonNull(holdWakeKeys, "hold wake keys");
        Objects.requireNonNull(admissionWeight, "admission weight");
        Set<ScheduleId> audited = auditExpired(instant.ticks());
        List<ScheduledAction> executed = new ArrayList<>();
        int weight = 0;
        for (Iterator<ScheduledAction> iterator = runnable.iterator(); iterator.hasNext();) {
            ScheduledAction next = iterator.next();
            if (next.dueAt().compareTo(instant) > 0) {
                break;
            }
            if (!eligible.test(next)) {
                Set<SubjectId> keys = holdWakeKeys.apply(next);
                if (!keys.isEmpty()) {
                    iterator.remove();
                    park(next, keys, instant.ticks());
                }
                continue;
            }
            countMissedWake(audited, next);
            int cost = admissionWeight.applyAsInt(next);
            if (cost < 1) throw new IllegalArgumentException("scheduled admission cost must be positive");
            if (executed.size() == budget.maxActions() || cost > budget.maxWeight() - weight) {
                return new ScheduledWork(executed, next);
            }
            executed.add(next);
            weight = Math.addExact(weight, cost);
        }
        return new ScheduledWork(executed, null);
    }

    /** A committed owner transition re-admits only matching waiters, in original due order. */
    public void wake(Set<SubjectId> keys) {
        wake(keys, 0L);
    }
    public void wake(Set<SubjectId> keys, long atTick) {
        Set<ScheduleId> ids = new HashSet<>();
        for (SubjectId key : keys) ids.addAll(parkedByKey.getOrDefault(key, Set.of()));
        ids.forEach(id -> unpark(id, atTick));
    }

    /** A derived-index failure falls back to ordinary eligibility checks, never lost work. */
    public void wakeAll() {
        wakeAll(0L);
    }
    public void wakeAll(long atTick) {
        Set<ScheduleId> ids = Set.copyOf(parked.keySet());
        ids.forEach(id -> unpark(id, atTick));
    }

    private void park(ScheduledAction action, Set<SubjectId> keys, long now) {
        Set<SubjectId> exact = Set.copyOf(keys);
        if (exact.isEmpty() || parked.containsKey(action.id()))
            throw new IllegalArgumentException("held action needs new exact wake keys");
        long audit = now > Long.MAX_VALUE - HOLD_AUDIT_TICKS ? Long.MAX_VALUE : now + HOLD_AUDIT_TICKS;
        parked.put(action.id(), new Parked(action, exact, audit));
        readySince.remove(action.id());
        for (SubjectId key : exact) parkedByKey.computeIfAbsent(key, ignored -> new HashSet<>()).add(action.id());
        auditAt.computeIfAbsent(audit, ignored -> new HashSet<>()).add(action.id());
    }

    private Set<ScheduleId> auditExpired(long now) {
        if (auditAt.isEmpty() || auditAt.firstKey() > now) return Set.of();
        Set<ScheduleId> audited = new HashSet<>();
        while (!auditAt.isEmpty() && auditAt.firstKey() <= now) {
            Set<ScheduleId> ids = Set.copyOf(auditAt.firstEntry().getValue());
            ids.forEach(id -> {
                unpark(id, now);
                audited.add(id);
            });
        }
        return audited;
    }

    private void countMissedWake(Set<ScheduleId> audited, ScheduledAction action) {
        if (audited.contains(action.id()) && auditReadyWithoutWake < Long.MAX_VALUE) auditReadyWithoutWake++;
    }

    private void unpark(ScheduleId id, long atTick) {
        Parked entry = forgetParked(id);
        if (entry != null && byId.get(id) == entry.action()) {
            runnable.add(entry.action());
            readySince.put(id, Math.max(entry.action().dueAt().ticks(), atTick));
        }
    }

    private Parked forgetParked(ScheduleId id) {
        Parked entry = parked.remove(id);
        if (entry == null) return null;
        for (SubjectId key : entry.keys()) {
            Set<ScheduleId> ids = parkedByKey.get(key);
            ids.remove(id);
            if (ids.isEmpty()) parkedByKey.remove(key);
        }
        Set<ScheduleId> auditIds = auditAt.get(entry.auditTick());
        auditIds.remove(id);
        if (auditIds.isEmpty()) auditAt.remove(entry.auditTick());
        return entry;
    }

    /** Acknowledges the current head after its immutable completion event has committed. */
    public void acknowledge(ScheduledAction action) {
        Objects.requireNonNull(action, "action");
        ScheduledAction current = ordered.isEmpty() ? null : ordered.first();
        if (!action.equals(current)) {
            throw new IllegalStateException("scheduled action acknowledgement is not the queue head: " + action.id().value());
        }
        ordered.pollFirst();
        immutableSnapshot = null;
        byId.remove(action.id());
        removeOwnerKind(action);
        removeDeadline(action);
        runnable.remove(action);
        forgetParked(action.id());
    }

    private void removeOwnerKind(ScheduledAction action) {
        ReviewKey key = ReviewKey.of(action);
        var values = byOwnerKind.get(key);
        values.remove(action);
        if (values.isEmpty()) byOwnerKind.remove(key);
    }

    private void removeDeadline(ScheduledAction action) {
        long tick = action.dueAt().ticks();
        int count = deadlines.get(tick);
        if (count == 1) deadlines.remove(tick); else deadlines.put(tick, count - 1);
        readySince.remove(action.id());
    }

    /** Reads indexed due work only; future schedules are counted by distinct deadline buckets. */
    public FrontierExecutionMetrics.QueuePressure pressure(SimInstant instant, Predicate<ScheduledAction> eligible) {
        long now = instant.ticks();
        int due = deadlines.headMap(now, true).values().stream().mapToInt(Integer::intValue).sum();
        int ready = 0; long oldestReady = 0, oldestDeadline = 0;
        for (ScheduledAction action : runnable) {
            if (action.dueAt().ticks() > now) break;
            if (!eligible.test(action)) continue;
            ready++;
            oldestReady = Math.max(oldestReady, Math.max(0, now - readySince.getOrDefault(action.id(), now)));
            oldestDeadline = Math.max(oldestDeadline, now - action.dueAt().ticks());
        }
        return new FrontierExecutionMetrics.QueuePressure(now, size(), ready, due - ready, size() - due, oldestReady, oldestDeadline);
    }

    public List<ScheduledAction> snapshot() {
        if (immutableSnapshot == null) immutableSnapshot = List.copyOf(ordered);
        return immutableSnapshot;
    }

    public int size() {
        return ordered.size();
    }

    /** Diagnostic only: parked actions remain canonical members but are not runnable work. */
    public int parkedCount() { return parked.size(); }

    public Optional<SimInstant> nextExecutionBoundary() {
        Long due = runnable.isEmpty() ? null : runnable.first().dueAt().ticks();
        Long audit = auditAt.isEmpty() ? null : auditAt.firstKey();
        if (due == null && audit == null) return Optional.empty();
        return Optional.of(new SimInstant(due == null ? audit : audit == null ? due : Math.min(due, audit)));
    }

    /** Lower bound: an audit found an eligible wait that no owner signal had woken. */
    public long auditReadyWithoutWake() { return auditReadyWithoutWake; }

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

        /** Coalesces only explicitly requested reviews, never ordinary Created facts. */
        void requestReconsideration(ScheduledAction requested) {
            requireOpen(); Objects.requireNonNull(requested, "requested review");
            ScheduledAction exactId = find(requested.id());
            if (exactId != null && !exactId.equals(requested)) throw new IllegalArgumentException("review ID collision");
            ReviewKey key = ReviewKey.of(requested);
            List<ScheduledAction> pending = new ArrayList<>();
            for (ScheduledAction action : base.byOwnerKind.getOrDefault(key, java.util.Collections.emptyNavigableSet()))
                if (!removed.contains(action.id())) pending.add(action);
            for (ScheduledAction action : created.values()) if (ReviewKey.of(action).equals(key)) pending.add(action);
            for (ScheduledAction action : pending) {
                if (action.priority() != requested.priority() || action.weight() != requested.weight())
                    throw new IllegalArgumentException("review requests disagree on declared priority or cost: " + key);
            }
            ScheduledAction retained = pending.stream().min(ScheduledAction::compareTo).orElse(null);
            // Keep the first retained causal identity at equal/later deadlines. An earlier
            // request replaces it with its own identity. Consumption removes membership before
            // subsequent requests, so a change during a review always has a successor.
            ScheduledAction selected = retained == null || requested.dueAt().compareTo(retained.dueAt()) < 0
                    ? requested : retained;
            for (ScheduledAction action : pending) if (!action.equals(selected)) cancel(action.id());
            if (retained == null || selected == requested) {
                if (find(requested.id()) != null) {
                    if (!requested.equals(find(requested.id()))) throw new IllegalArgumentException("review ID collision");
                } else schedule(requested);
            }
        }

        boolean cancel(ScheduleId id) {
            requireOpen(); Objects.requireNonNull(id, "schedule id");
            ScheduledAction action = find(id);
            if (action == null) return false;
            if (created.remove(id) == null) removed.add(id);
            return true;
        }

        ScheduledAction head() {
            return head(ignored -> true);
        }

        ScheduledAction head(Predicate<ScheduledAction> eligible) {
            ScheduledAction retained = base.ordered.stream().filter(action -> !removed.contains(action.id()))
                    .filter(eligible).findFirst().orElse(null);
            ScheduledAction added = created.values().stream().filter(eligible).min(ScheduledAction::compareTo).orElse(null);
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

        /**
         * Exact post-commit cardinality without copying unchanged future work. The engine checks
         * this before WAL durability, so an over-cap plan cannot become canonical history.
         */
        int projectedSize() {
            return Math.addExact(Math.subtractExact(base.ordered.size(), removed.size()), created.size());
        }

        void requireCapacity(int maximum) {
            if (maximum <= 0) throw new IllegalArgumentException("scheduled-action capacity must be positive");
            int projected = projectedSize();
            if (projected > maximum) {
                throw new IllegalStateException("scheduled-action capacity exhausted: " + projected + ">" + maximum);
            }
        }

        List<ScheduledAction> retainedChanges() {
            requireOpen();
            return List.copyOf(created.values());
        }

        List<ScheduledAction> snapshot() {
            requireOpen();
            List<ScheduledAction> values = new java.util.ArrayList<>(base.ordered.size() - removed.size() + created.size());
            for (ScheduledAction action : base.ordered) if (!removed.contains(action.id())) values.add(action);
            values.addAll(created.values()); values.sort(ScheduledAction::compareTo);
            return List.copyOf(values);
        }

        /** Applies a prevalidated overlay. No allocation proportional to unchanged future work occurs. */
        void commit() {
            requireOpen();
            removed.forEach(base::cancel);
            created.values().forEach(base::schedule);
            committed = true;
        }

        ScheduledAction find(ScheduleId id) {
            requireOpen(); Objects.requireNonNull(id, "schedule id");
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
