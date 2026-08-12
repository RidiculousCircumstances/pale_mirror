package io.farfrontier.palemirror.domain;

import java.util.List;

/** Explicit command result used by persistence bridges to detect eventless mutations. */
public record DomainCommandOutcome(boolean changed, List<DomainEvent> events) {
    public DomainCommandOutcome {
        events = List.copyOf(events);
    }
}
