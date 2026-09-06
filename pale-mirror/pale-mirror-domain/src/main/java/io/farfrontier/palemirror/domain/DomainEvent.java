package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Compact causal record. Payload expansion is deliberately postponed until later content slices. */
public record DomainEvent(
        String eventId,
        DomainEventType type,
        WorldObjectId subject,
        long simulationStep,
        String causationId,
        String correlationId
) {
    public DomainEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");
    }
}
