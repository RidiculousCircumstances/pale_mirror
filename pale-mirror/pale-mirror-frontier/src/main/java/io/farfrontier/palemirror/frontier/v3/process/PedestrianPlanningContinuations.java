package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAdvisory;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledPreparation;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianCalculationMemory;
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
                owner.restore(wait.action(), wait.advice().observation().requests());
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
    static boolean held(ScheduledAction action, java.util.function.BooleanSupplier ownerAssessment) {
        if (waiting(action)) return true;
        boolean held = ownerAssessment.getAsBoolean();
        var index = current; if (index != null) index.ownerHeld(action, held);
        return held;
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
        var observation = PedestrianRoutePlanning.observe(index.reads(action), planning);
        return publish(index, action, observation, observation.reads());
    }
    /** Detached advice captures route evidence; only accepted owner completion publishes dependencies. */
    static io.farfrontier.palemirror.frontier.v3.kernel.ScheduledPreparation prepare(
            ScheduledAction action, Supplier<List<ProposedEvent>> planning) {
        var index = current;
        var view = PedestrianRoutePlanning.readView();
        var reads = index == null ? PedestrianCalculationMemory.empty() : index.reads(action);
        return () -> {
            var prepared = PedestrianRoutePlanning.prepare(view, reads, planning);
            return () -> {
                if (current != index) throw new IllegalStateException("prepared planning lost its runtime owner");
                var ready = prepared.acquire();
                if (index == null) return prepared.observation().result();
                var result = publish(index, action, prepared.observation(), prepared.reads());
                index.changed(ready);
                return result;
            };
        };
    }
    /** Advice and its fresh exact continuation share one declared route-dependency story.
     * Worker reads never acquire native work; only revalidated owner completion does. */
    static io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAdvisory<io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState> advisory(
            ScheduledAction action,
            io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAdvisory<io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState> advice) {
        var index = current; var view = PedestrianRoutePlanning.readView();
        var reads = index == null ? PedestrianCalculationMemory.empty() : index.reads(action);
        return () -> {
            var prepared = PedestrianRoutePlanning.prepare(view, reads, advice::prepare);
            return new ScheduledAdvisory.Completion<FrontierWorldState>() {
                @Override public String rejectionReason() { return prepared.observation().result().rejectionReason(); }
                @Override public java.util.Optional<ScheduledPreparation> revalidate(FrontierWorldState state, SimInstant at) {
                    if (current != index) throw new IllegalStateException("advice lost its planning runtime owner");
                    return prepared.observation().result().revalidate(state, at).map(preparation -> {
                        var ready = prepared.acquire();
                        if (index != null) index.changed(ready);
                        var exactView = PedestrianRoutePlanning.readView();
                        return () -> {
                            var exact = PedestrianRoutePlanning.prepare(exactView, prepared.reads(), preparation::prepare);
                            return () -> {
                                if (current != index) throw new IllegalStateException("advice continuation lost its planning owner");
                                var changes = exact.acquire();
                                var completion = PedestrianRoutePlanning.observe(exact.reads(), exact.observation().result());
                                if (index == null) return completion.result();
                                var requests = new java.util.LinkedHashSet<>(prepared.observation().requests());
                                requests.addAll(exact.observation().requests()); requests.addAll(completion.requests());
                                var result = publish(index, action, new PedestrianRoutePlanning.Observation<>(completion.result(), java.util.List.copyOf(requests)), exact.reads().merge(completion.reads()));
                                index.changed(changes); return result;
                            };
                        };
                    });
                }
            };
        };
    }
    private static List<ProposedEvent> publish(PedestrianPlanningWakeIndex index, ScheduledAction action,
            PedestrianRoutePlanning.Observation<List<ProposedEvent>> observation, PedestrianCalculationMemory reads) {
        // A calculation-only wait changes no business state and has no elapsed-time
        // deadline. Retain the exact due generation instead of inventing a future
        // retry which later needs a second durable command to become runnable.
        // A real transition owns its successor timing and is never rewritten here.
        var result = observation.result();
        boolean calculationOnly = !observation.requests().isEmpty() && result.size() == 1
                && result.getFirst().payload() instanceof ScheduleEffect.Rescheduled replacement
                && replacement.scheduleId().equals(action.id())
                && result.getFirst().subject().equals(action.subject())
                && replacement.replacement().equals(new ScheduledAction(action.id(), replacement.replacement().dueAt(),
                        action.priority(), action.subject(), action.kind(), action.weight()));
        if (calculationOnly) result = List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(), action)));
        ScheduledAction successor = null;
        for (var event : result) {
            if (event.payload() instanceof ScheduleEffect.Rescheduled replacement && replacement.scheduleId().equals(action.id()))
                successor = replacement.replacement();
            else if (event.payload() instanceof ScheduleEffect.Created created && created.action().id().equals(action.id()))
                successor = created.action();
            else if (event.payload() instanceof ScheduleEffect.Cancelled cancelled && cancelled.scheduleId().equals(action.id()))
                successor = null;
        }
        if (successor == null) index.remove(action.id());
        else index.stage(action, successor, observation.requests(), calculationOnly ? reads : PedestrianCalculationMemory.empty());
        return result;
    }
}
