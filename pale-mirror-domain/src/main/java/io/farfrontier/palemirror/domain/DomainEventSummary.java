package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Compact causal receipt for detailed events evicted from the bounded window. */
public record DomainEventSummary(WorldObjectId subject, DomainEventType type, long count,
                                 long firstStep, long lastStep) {
    public DomainEventSummary {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(type, "type");
        if (count < 1 || firstStep < 0 || lastStep < firstStep) {
            throw new IllegalArgumentException("Invalid event summary");
        }
    }

    DomainEventSummary include(DomainEvent event) {
        return new DomainEventSummary(subject, type, count + 1, Math.min(firstStep, event.simulationStep()),
                Math.max(lastStep, event.simulationStep()));
    }
}
