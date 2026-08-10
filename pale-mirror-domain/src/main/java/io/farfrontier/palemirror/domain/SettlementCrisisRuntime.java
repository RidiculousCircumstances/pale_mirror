package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.List;

/** Resolves a settlement crisis from canonical facts, never from UI or adapter actions. */
public final class SettlementCrisisRuntime {
    private final DomainEventFactory events;

    SettlementCrisisRuntime(DomainEventFactory events) { this.events = events; }

    public List<DomainEvent> accept(WorldState state, ScenarioInstance scenario) {
        if (scenario.status() != ScenarioStatus.OFFERED) return List.of();
        scenario.setStatus(ScenarioStatus.RESPOND);
        DomainEvent accepted = events.create(state, DomainEventType.SCENARIO_ACCEPTED, scenario.target(), scenario.sourceEventId());
        state.addEvent(accepted);
        return List.of(accepted);
    }

    public List<DomainEvent> reconcile(WorldState state) {
        List<DomainEvent> produced = new ArrayList<>();
        for (ScenarioInstance scenario : state.scenarios()) {
            if (scenario.archetype() != ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS
                    || (scenario.status() != ScenarioStatus.ASSESS && scenario.status() != ScenarioStatus.RESPOND)) continue;
            LivingRegionState region = state.livingRegions().stream()
                    .filter(value -> value.settlementId().equals(scenario.target())).findFirst().orElse(null);
            if (region == null) continue;
            boolean mineRecovered = state.facility(region.primaryFacilityId())
                    .map(value -> value.status() == FacilityStatus.OPERATIONAL).orElse(false);
            boolean alternateRoute = state.route(region.alternateRouteId())
                    .map(value -> value.transferableCapacity() >= state.settlement(region.settlementId())
                            .map(settlement -> settlement.consumption(ResourceKind.IRON)).orElse(Integer.MAX_VALUE)).orElse(false);
            boolean evacuated = state.migrantGroups().stream().anyMatch(value -> value.originSettlement().equals(region.settlementId()));
            if (!mineRecovered && !alternateRoute && !evacuated) continue;
            scenario.setStatus(ScenarioStatus.RESOLVED);
            region.resolve();
            DomainEvent resolved = events.create(state, DomainEventType.SCENARIO_RESOLVED, scenario.target(), scenario.sourceEventId());
            state.addEvent(resolved);
            produced.add(resolved);
        }
        return List.copyOf(produced);
    }
}
