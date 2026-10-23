package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;
import java.util.List;
import java.util.function.Supplier;

/** Connects declared process continuations to their actual calculation dependencies, not their business stages. */
public final class PedestrianPlanningContinuations {
    private static PedestrianPlanningWakeIndex current;
    private PedestrianPlanningContinuations() { }
    record RecoveryCandidate(ScheduledAction action, Supplier<List<ProposedEvent>> planning) { }
    private record RecoveredWait(ScheduledAction action, PedestrianRoutePlanning.Prepared<List<ProposedEvent>> advice) { }
    /** Volatile hints only: effects are discarded; changed or already registered continuations win. */
    public static final class Recovery {
        private final PedestrianPlanningWakeIndex owner;
        private final List<RecoveredWait> waits;
        private Recovery(PedestrianPlanningWakeIndex owner, List<RecoveredWait> waits) { this.owner = owner; this.waits = List.copyOf(waits); }
        public void publish(java.util.function.Predicate<ScheduledAction> retained) {
            if (current != owner) throw new IllegalStateException("planning recovery lost its runtime owner");
            for (var wait : waits) if (retained.test(wait.action()) && !owner.tracks(wait.action())) {
                var ready = wait.advice().acquire();
                owner.replace(wait.action(), wait.advice().observation().requests());
                owner.changed(ready);
            }
        }
    }
    static Supplier<Recovery> prepareRestore(Supplier<List<RecoveryCandidate>> candidates) {
        var owner = java.util.Objects.requireNonNull(current, "planning recovery owner");
        var view = PedestrianRoutePlanning.readView();
        return () -> {
            var waits = new java.util.ArrayList<RecoveredWait>();
            for (var candidate : candidates.get()) {
                var advice = PedestrianRoutePlanning.prepare(view, candidate.planning());
                if (!advice.observation().requests().isEmpty()) waits.add(new RecoveredWait(candidate.action(), advice));
            }
            return new Recovery(owner, waits);
        };
    }
    static boolean waiting(ScheduledAction action) {
        var index = current;
        return index != null && index.waiting(action);
    }
    static void markParked(ScheduledAction action) {
        if (current == null) throw new IllegalStateException("calculation hold has no continuation owner");
        current.markParked(action);
    }
    public static synchronized AutoCloseable bind(PedestrianPlanningWakeIndex index) {
        if (current != null) throw new IllegalStateException("planning continuations already have an owner");
        current = java.util.Objects.requireNonNull(index);
        return () -> { synchronized (PedestrianPlanningContinuations.class) {
            if (current != index) throw new IllegalStateException("foreign planning continuation release");
            current = null;
        } };
    }
    static List<ProposedEvent> plan(ScheduledAction action, Supplier<List<ProposedEvent>> planning) {
        var index = current;
        if (index == null) return planning.get();
        var observation = PedestrianRoutePlanning.observe(planning);
        return publish(index, action, observation);
    }
    /** Detached advice captures route evidence; only accepted owner completion publishes dependencies. */
    static io.farfrontier.palemirror.frontier.v3.kernel.ScheduledPreparation prepare(
            ScheduledAction action, Supplier<List<ProposedEvent>> planning) {
        var index = current;
        var view = PedestrianRoutePlanning.readView();
        return () -> {
            var prepared = PedestrianRoutePlanning.prepare(view, planning);
            return () -> {
                if (current != index) throw new IllegalStateException("prepared planning lost its runtime owner");
                var ready = prepared.acquire();
                if (index == null) return prepared.observation().result();
                var result = publish(index, action, prepared.observation());
                index.changed(ready);
                return result;
            };
        };
    }
    private static List<ProposedEvent> publish(PedestrianPlanningWakeIndex index, ScheduledAction action,
            PedestrianRoutePlanning.Observation<List<ProposedEvent>> observation) {
        ScheduledAction successor = null;
        for (var event : observation.result()) {
            if (event.payload() instanceof ScheduleEffect.Rescheduled replacement && replacement.scheduleId().equals(action.id()))
                successor = replacement.replacement();
            else if (event.payload() instanceof ScheduleEffect.Created created && created.action().id().equals(action.id()))
                successor = created.action();
            else if (event.payload() instanceof ScheduleEffect.Cancelled cancelled && cancelled.scheduleId().equals(action.id()))
                successor = null;
        }
        if (successor == null) index.remove(action.id());
        else index.replace(successor, observation.requests());
        return observation.result();
    }
}
