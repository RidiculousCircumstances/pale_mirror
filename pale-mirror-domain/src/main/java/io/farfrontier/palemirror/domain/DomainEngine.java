package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic domain rules; no Minecraft classes and no wall-clock access. */
public final class DomainEngine {
    public List<DomainEvent> advanceSimulation(WorldState state, int steps) {
        List<DomainEvent> produced = new ArrayList<>();
        for (int index = 0; index < steps; index++) {
            state.setSimulationStep(state.simulationStep() + 1);
            state.facilities().stream().sorted(Comparator.comparing(FacilityState::id)).forEach(facility -> {
                if (facility.status() == FacilityStatus.OPERATIONAL && facility.infectionPressure() >= facility.infectionThreshold()) {
                    facility.infect();
                    produced.add(event(state, DomainEventType.MINE_INFECTED, facility.id(), null));
                    produced.add(event(state, DomainEventType.FACILITY_DISABLED, facility.id(), null));
                } else if (facility.advanceRecovery()) {
                    produced.add(event(state, DomainEventType.FACILITY_OPERATIONAL, facility.id(), null));
                }
            });
        }
        produced.forEach(state::addEvent);
        return produced;
    }

    public List<DomainEvent> controllerDestroyed(WorldState state, WorldObjectId facilityId, String causationId) {
        FacilityState facility = state.facility(facilityId).orElseThrow(() -> new IllegalArgumentException("Unknown facility " + facilityId));
        if (facility.status() != FacilityStatus.INFECTED) return List.of();
        facility.beginRecovery(1);
        List<DomainEvent> produced = List.of(
                event(state, DomainEventType.THREAT_CONTROLLER_DESTROYED, facilityId, causationId),
                event(state, DomainEventType.FACILITY_RECOVERING, facilityId, causationId));
        produced.forEach(state::addEvent);
        return produced;
    }

    public DomainEvent event(WorldState state, DomainEventType type, WorldObjectId subject, String causationId) {
        String id = "pm:event:" + state.nextEventSequence();
        return new DomainEvent(id, type, subject, state.simulationStep(), causationId, causationId == null ? id : causationId);
    }
}
