package io.farfrontier.palemirror.domain;

import java.util.List;

/** Stateful scenario executor. Physical work is requested elsewhere through a plan. */
public final class ScenarioRuntime {
    private final DomainEventFactory events;

    ScenarioRuntime(DomainEventFactory events) {
        this.events = events;
    }

    public List<DomainEvent> accept(WorldState state, String scenarioId) {
        ScenarioInstance scenario = state.scenario(scenarioId).orElseThrow(() -> new IllegalArgumentException("Unknown scenario " + scenarioId));
        if (scenario.status() != ScenarioStatus.OFFERED) return List.of();
        FacilityState facility = state.facility(scenario.target()).orElseThrow();
        if (facility.status() == FacilityStatus.RECOVERING || facility.status() == FacilityStatus.OPERATIONAL) {
            scenario.setStatus(ScenarioStatus.RESOLVED);
            DomainEvent resolved = events.create(state, DomainEventType.SCENARIO_RESOLVED, scenario.target(), scenario.sourceEventId());
            state.addEvent(resolved);
            return List.of(resolved);
        }
        scenario.setStatus(ScenarioStatus.INVESTIGATE);
        DomainEvent accepted = events.create(state, DomainEventType.SCENARIO_ACCEPTED, scenario.target(), scenario.sourceEventId());
        state.addEvent(accepted);
        return List.of(accepted);
    }

    public List<DomainEvent> playerEntered(WorldState state, StoryAudienceId audience, WorldObjectId facilityId) {
        return state.scenarios().stream()
                .filter(scenario -> scenario.audience().equals(audience) && scenario.target().equals(facilityId) && scenario.status() == ScenarioStatus.INVESTIGATE)
                .map(scenario -> {
                    scenario.setStatus(ScenarioStatus.RECOVER);
                    DomainEvent event = events.create(state, DomainEventType.FACILITY_INVESTIGATED, facilityId, scenario.sourceEventId());
                    state.addEvent(event);
                    return event;
                }).toList();
    }

    public List<DomainEvent> reconcileRecovery(WorldState state, WorldObjectId facilityId) {
        return state.scenarios().stream()
                .filter(scenario -> scenario.target().equals(facilityId) && (scenario.status() == ScenarioStatus.RECOVER || scenario.status() == ScenarioStatus.INVESTIGATE))
                .map(scenario -> {
                    scenario.setStatus(ScenarioStatus.RESOLVED);
                    DomainEvent event = events.create(state, DomainEventType.SCENARIO_RESOLVED, facilityId, scenario.sourceEventId());
                    state.addEvent(event);
                    return event;
                }).toList();
    }
}
