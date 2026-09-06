package io.farfrontier.palemirror.domain;

import java.util.List;

/** Reconciles confirmed threat-controller observations into facility recovery. */
public final class ThreatLifecycle {
    private final DomainEventFactory events;

    ThreatLifecycle(DomainEventFactory events) {
        this.events = events;
    }

    public List<DomainEvent> controllerDestroyed(WorldState state, WorldObjectId facilityId, String causationId) {
        FacilityState facility = state.facility(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown facility " + facilityId));
        if (facility.status() != FacilityStatus.INFECTED) return List.of();

        facility.beginRecovery(1);
        List<DomainEvent> produced = List.of(
                events.create(state, DomainEventType.THREAT_CONTROLLER_DESTROYED, facilityId, causationId),
                events.create(state, DomainEventType.FACILITY_RECOVERING, facilityId, causationId));
        produced.forEach(state::addEvent);
        return produced;
    }
}
