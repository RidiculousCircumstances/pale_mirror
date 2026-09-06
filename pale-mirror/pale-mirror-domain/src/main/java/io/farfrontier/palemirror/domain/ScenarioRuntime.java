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
        if (scenario.archetype() == ScenarioArchetype.DEVELOPMENT_OPPORTUNITY
                || scenario.archetype() == ScenarioArchetype.RESETTLEMENT_OPPORTUNITY) {
            DomainEvent source = state.history().stream()
                    .filter(event -> event.eventId().equals(scenario.sourceEventId())).findFirst().orElse(null);
            DevelopmentIntent intent = source == null || source.causationId() == null ? null
                    : state.developmentIntent(source.causationId()).orElse(null);
            if (intent == null || intent.state() != DevelopmentIntentState.PLANNED) {
                String reason = "Opportunity is no longer pending in canonical settlement state";
                scenario.block(reason);
                DomainEvent blocked = events.create(state, DomainEventType.SCENARIO_BLOCKED,
                        scenario.target(), scenario.sourceEventId());
                state.addEvent(blocked);
                return List.of(blocked);
            }
            scenario.setStatus(ScenarioStatus.RESPOND);
            DomainEvent accepted = events.create(state, DomainEventType.SCENARIO_ACCEPTED,
                    scenario.target(), scenario.sourceEventId());
            state.addEvent(accepted);
            return List.of(accepted);
        }
        if (scenario.archetype() != ScenarioArchetype.INVESTIGATION_RECOVERY) return List.of();
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

    public List<DomainEvent> cancel(WorldState state, String scenarioId, String reason) {
        ScenarioInstance scenario = state.scenario(scenarioId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario " + scenarioId));
        if (!scenario.cancel(reason)) return List.of();
        DomainEvent event = events.create(state, DomainEventType.SCENARIO_CANCELLED,
                scenario.target(), scenario.sourceEventId());
        state.addEvent(event);
        return List.of(event);
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

    /** Resolves a presented continuation only when its autonomous domain result is already true. */
    public List<DomainEvent> reconcileOpportunity(WorldState state, WorldObjectId communityId,
                                                   ScenarioArchetype archetype, String outcome) {
        return state.scenarios().stream()
                .filter(scenario -> scenario.target().equals(communityId) && scenario.archetype() == archetype)
                .filter(scenario -> !scenario.status().isTerminal())
                .map(scenario -> {
                    if (!scenario.resolve(outcome)) return null;
                    DomainEvent event = events.create(state, DomainEventType.SCENARIO_RESOLVED,
                            communityId, scenario.sourceEventId());
                    state.addEvent(event);
                    return event;
                }).filter(java.util.Objects::nonNull).toList();
    }
}
