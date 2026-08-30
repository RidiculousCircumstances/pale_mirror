package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded retention sweep; it does not mutate world state except through its durable receipt event. */
final class TerminalLogisticsProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:terminal-logistics-retention");
    private static final int MAX_COMPACTIONS_PER_REVIEW = 12;
    private static final long INTERVAL = 6_000L;
    private TerminalLogisticsProcess() { }

    static ScheduledAction review(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:terminal-logistics-retention-" + ordinal), new SimInstant(dueAt), 0,
                SYSTEM, "frontier.terminal_logistics.retention", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        if (!SYSTEM.equals(action.subject())) throw new IllegalArgumentException("terminal logistics retention has a foreign subject");
        List<ProposedEvent> events = new ArrayList<>();
        state.operations().values().stream().sorted(Comparator.comparing(RouteOperation::id)).filter(operation -> state.canCompactTerminalLogistics(operation.id()))
                .limit(MAX_COMPACTIONS_PER_REVIEW).forEach(operation -> events.add(new ProposedEvent(operation.settlementId(), new TerminalLogisticsCompacted(operation.id()))));
        events.add(new ProposedEvent(SYSTEM, new ScheduleEffect.Created(review(nextOrdinal(action), Math.addExact(action.dueAt().ticks(), INTERVAL)))));
        return List.copyOf(events);
    }

    private static int nextOrdinal(ScheduledAction action) {
        String prefix = "schedule:terminal-logistics-retention-";
        String value = action.id().value();
        if (!value.startsWith(prefix)) throw new IllegalArgumentException("terminal logistics retention has an invalid schedule id");
        return Math.addExact(Integer.parseInt(value.substring(prefix.length())), 1);
    }
}
