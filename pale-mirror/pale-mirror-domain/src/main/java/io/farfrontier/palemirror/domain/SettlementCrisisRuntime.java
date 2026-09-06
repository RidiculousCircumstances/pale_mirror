package io.farfrontier.palemirror.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves presentation from canonical supply facts; it never creates the crisis itself. */
public final class SettlementCrisisRuntime {
    public static final String PRIMARY_OUTCOME = "PRIMARY_SUPPLY_RESTORED";
    public static final String ALTERNATE_OUTCOME = "ALTERNATE_SUPPLY_VALIDATED";
    public static final String EVACUATED_OUTCOME = "COMMUNITY_EVACUATED";

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
        for (LivingRegionState region : state.livingRegions().stream()
                .sorted(Comparator.comparing(LivingRegionState::id)).toList()) {
            List<ScenarioInstance> linked = state.scenarios().stream()
                    .filter(value -> value.target().equals(region.communityId()))
                    .filter(value -> value.archetype() == ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS)
                    .filter(value -> !value.status().isTerminal())
                    .sorted(Comparator.comparing(ScenarioInstance::id)).toList();
            boolean incidentStarted = region.incidentResolvedAtStep() >= 0 || region.incidentArmedAtStep() >= 0
                    && state.history().stream()
                    .anyMatch(value -> value.type() == DomainEventType.MINE_INFECTED
                            && value.subject().equals(region.primaryFacilityId())
                            && value.simulationStep() >= region.incidentArmedAtStep());
            if (!incidentStarted) continue;
            ResourceAccount iron = state.economy(region.communityId()).map(value -> value.require(ResourceKind.IRON)).orElse(null);
            if (iron == null) continue;
            boolean primaryRecovered = state.facility(region.primaryFacilityId())
                    .map(value -> value.status() == FacilityStatus.OPERATIONAL).orElse(false);
            boolean alternateValidated = state.routeContract(region.alternateRouteId())
                    .map(value -> value.transferableCapacity(state.simulationStep()) >= iron.effectiveConsumption())
                    .orElse(false);
            boolean evacuated = state.populationGroups(region.communityId()).stream().allMatch(value ->
                    value.disposition() == PopulationDisposition.DISPLACED
                            || value.disposition() == PopulationDisposition.RESETTLED);
            String outcome = region.incidentResolvedAtStep() >= 0 ? region.incidentOutcome()
                    : primaryRecovered ? PRIMARY_OUTCOME : alternateValidated ? ALTERNATE_OUTCOME
                    : evacuated ? EVACUATED_OUTCOME : "";
            if (outcome.isEmpty()) continue;
            DomainEventType outcomeType = primaryRecovered ? DomainEventType.PRIMARY_SUPPLY_RESTORED
                    : alternateValidated ? DomainEventType.ALTERNATE_SUPPLY_VALIDATED : DomainEventType.COMMUNITY_EVACUATED;
            if (region.resolveIncident(outcome, state.simulationStep())) {
                String cause = linked.isEmpty() ? incidentCausation(state, region) : linked.getFirst().sourceEventId();
                DomainEvent outcomeEvent = events.create(state, outcomeType, region.communityId(), cause);
                state.addEvent(outcomeEvent);
                produced.add(outcomeEvent);
            }
            for (ScenarioInstance scenario : linked) {
                if (!scenario.resolve(outcome)) continue;
                DomainEvent resolved = events.create(state, DomainEventType.SCENARIO_RESOLVED,
                        scenario.target(), scenario.sourceEventId());
                state.addEvent(resolved);
                produced.add(resolved);
            }
        }
        return List.copyOf(produced);
    }

    private static String incidentCausation(WorldState state, LivingRegionState region) {
        return state.history().stream().filter(value -> value.type() == DomainEventType.MINE_INFECTED
                        && value.subject().equals(region.primaryFacilityId()))
                .reduce((ignored, latest) -> latest).map(DomainEvent::eventId)
                .orElse("region-incident:" + region.id());
    }
}
