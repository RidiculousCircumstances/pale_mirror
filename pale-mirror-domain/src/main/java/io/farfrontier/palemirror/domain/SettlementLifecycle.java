package io.farfrontier.palemirror.domain;

import java.util.List;

/** Applies durable settlement decisions. It deliberately has no physical-world knowledge. */
public final class SettlementLifecycle {
    private final DomainEventFactory events;

    SettlementLifecycle(DomainEventFactory events) { this.events = events; }

    public List<DomainEvent> evacuate(WorldState state, WorldObjectId settlementId, WorldObjectId migrantGroupId,
                                      int population, String causationId) {
        SettlementState settlement = state.settlement(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown settlement " + settlementId));
        if (state.migrantGroup(migrantGroupId).isPresent() || !settlement.evacuate(population)) return List.of();
        state.putMigrantGroup(new MigrantGroupState(migrantGroupId, settlementId, population, MigrantGroupStatus.SEEKING_SHELTER));
        List<DomainEvent> produced = List.of(
                events.create(state, DomainEventType.SETTLEMENT_EVACUATED, settlementId, causationId),
                events.create(state, DomainEventType.MIGRANT_GROUP_CREATED, migrantGroupId, causationId));
        produced.forEach(state::addEvent);
        return produced;
    }
}
