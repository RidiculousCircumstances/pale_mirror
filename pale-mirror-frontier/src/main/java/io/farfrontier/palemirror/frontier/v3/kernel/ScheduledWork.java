package io.farfrontier.palemirror.frontier.v3.kernel;

import java.util.List;

/** Ordered batch admitted by one deterministic work budget. */
public record ScheduledWork(List<ScheduledAction> executed, boolean dueWorkDeferred) {
    public ScheduledWork {
        executed = List.copyOf(executed);
    }
}
