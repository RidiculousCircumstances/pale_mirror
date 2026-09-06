package io.farfrontier.palemirror.frontier.v3.kernel;

import java.util.List;
import java.util.Optional;

/** Ordered batch admitted by one deterministic work budget. */
public record ScheduledWork(List<ScheduledAction> admitted, ScheduledAction blockedAction) {
    public ScheduledWork {
        admitted = List.copyOf(admitted);
    }

    public boolean dueWorkDeferred() {
        return blockedAction != null;
    }

    public Optional<ScheduledAction> blockedActionOptional() {
        return Optional.ofNullable(blockedAction);
    }
}
