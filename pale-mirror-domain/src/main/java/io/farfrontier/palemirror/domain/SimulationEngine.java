package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Advances deterministic facility simulation; it has no narrative or physical-world responsibilities. */
public final class SimulationEngine {
    private final DomainEventFactory events;

    SimulationEngine(DomainEventFactory events) {
        this.events = events;
    }

    public List<DomainEvent> advance(WorldState state, int steps) {
        if (steps < 0) throw new IllegalArgumentException("Simulation steps must not be negative");
        List<DomainEvent> produced = new ArrayList<>();
        for (int index = 0; index < steps; index++) {
            state.setSimulationStep(state.simulationStep() + 1);
            state.facilities().stream().sorted(Comparator.comparing(FacilityState::id)).forEach(facility -> {
                if (facility.status() == FacilityStatus.OPERATIONAL
                        && facility.infectionPressure() >= facility.infectionThreshold()) {
                    facility.infect();
                    produced.add(events.create(state, DomainEventType.MINE_INFECTED, facility.id(), null));
                    produced.add(events.create(state, DomainEventType.FACILITY_DISABLED, facility.id(), null));
                } else if (facility.advanceRecovery()) {
                    produced.add(events.create(state, DomainEventType.FACILITY_OPERATIONAL, facility.id(), null));
                }
            });
        }
        produced.forEach(state::addEvent);
        return List.copyOf(produced);
    }
}
