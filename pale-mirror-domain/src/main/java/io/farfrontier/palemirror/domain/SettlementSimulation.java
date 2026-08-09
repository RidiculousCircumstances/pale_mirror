package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reconciles abstract settlement supply and defense from authoritative facility production. */
public final class SettlementSimulation {
    private final DomainEventFactory events;

    SettlementSimulation(DomainEventFactory events) { this.events = events; }

    public List<DomainEvent> reconcile(WorldState state) {
        List<DomainEvent> produced = new ArrayList<>();
        state.settlements().stream().sorted(Comparator.comparing(SettlementState::id)).forEach(settlement -> {
            FacilityState source = state.facility(settlement.ironSource())
                    .orElseThrow(() -> new IllegalStateException("Settlement " + settlement.id() + " references missing iron source"));
            if (source.currentProduction() < settlement.expectedIronSupply()) {
                boolean wasDisrupted = settlement.supplyDisrupted();
                if (settlement.disrupt(source.currentProduction()) && !wasDisrupted) {
                    produced.add(events.create(state, DomainEventType.SETTLEMENT_SUPPLY_DISRUPTED, settlement.id(), source.id().value()));
                }
            } else if (settlement.restore()) {
                produced.add(events.create(state, DomainEventType.SETTLEMENT_SUPPLY_RESTORED, settlement.id(), source.id().value()));
            }
        });
        produced.forEach(state::addEvent);
        return List.copyOf(produced);
    }
}
