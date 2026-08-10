package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.List;

/** Resolves presentation from canonical supply facts; it never creates the crisis itself. */
public final class SettlementCrisisRuntime {
    public static final String PRIMARY_OUTCOME = "PRIMARY_SUPPLY_RESTORED";
    public static final String ALTERNATE_OUTCOME = "ALTERNATE_SUPPLY_VALIDATED";

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
            if (scenario.archetype() != ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS || scenario.status().isTerminal()
                    || scenario.status() == ScenarioStatus.BLOCKED) continue;
            LivingRegionState region = state.livingRegions().stream()
                    .filter(value -> value.communityId().equals(scenario.target())).findFirst().orElse(null);
            if (region == null) continue;
            ResourceAccount iron = state.economy(region.communityId()).map(value -> value.require(ResourceKind.IRON)).orElse(null);
            if (iron == null) continue;
            boolean primaryRecovered = state.facility(region.primaryFacilityId())
                    .map(value -> value.status() == FacilityStatus.OPERATIONAL).orElse(false);
            boolean alternateValidated = state.routeContract(region.alternateRouteId())
                    .map(value -> value.transferableCapacity(state.simulationStep()) >= iron.effectiveConsumption())
                    .orElse(false);
            String outcome = primaryRecovered ? PRIMARY_OUTCOME : alternateValidated ? ALTERNATE_OUTCOME : "";
            if (outcome.isEmpty() || !scenario.resolve(outcome)) continue;
            DomainEvent outcomeEvent = events.create(state, primaryRecovered ? DomainEventType.PRIMARY_SUPPLY_RESTORED
                    : DomainEventType.ALTERNATE_SUPPLY_VALIDATED, scenario.target(), scenario.sourceEventId());
            DomainEvent resolved = events.create(state, DomainEventType.SCENARIO_RESOLVED,
                    scenario.target(), scenario.sourceEventId());
            state.addEvent(outcomeEvent);
            state.addEvent(resolved);
            produced.add(outcomeEvent);
            produced.add(resolved);
        }
        return List.copyOf(produced);
    }
}
