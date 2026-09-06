package io.farfrontier.palemirror.domain;

/** Creates causally traceable events using only canonical world state. */
final class DomainEventFactory {
    DomainEvent create(WorldState state, DomainEventType type, WorldObjectId subject, String causationId) {
        String eventId = "pm:event:" + state.nextEventSequence();
        return new DomainEvent(eventId, type, subject, state.simulationStep(), causationId,
                causationId == null ? eventId : causationId);
    }
}
