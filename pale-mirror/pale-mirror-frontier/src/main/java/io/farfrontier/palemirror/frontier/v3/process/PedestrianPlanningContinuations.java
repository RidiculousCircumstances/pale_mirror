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
    /** Reconstruct dependencies against the retained action; proposed domain outcomes are deliberately discarded. */
    static void restore(ScheduledAction action, Supplier<List<ProposedEvent>> planning) {
        if (current == null) throw new IllegalStateException("planning recovery lacks its bound continuation owner");
        var observation = PedestrianRoutePlanning.observe(planning);
        current.replace(action, observation.requests());
    }
}
