package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import java.util.Objects;

/** Exact engine checkpoint action plus the authority revision that observed it. */
public record EngineScheduleBinding(Revision checkpointRevision, ScheduledAction action) {
    public EngineScheduleBinding {
        Objects.requireNonNull(checkpointRevision, "schedule binding revision");
        Objects.requireNonNull(action, "schedule binding action");
    }
}
