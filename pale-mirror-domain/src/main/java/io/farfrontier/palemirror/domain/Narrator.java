package io.farfrontier.palemirror.domain;

import java.util.List;

/** First rule-based storyteller: it turns a global incident into at most one audience scenario. */
public final class Narrator {
    private final DomainEventFactory events;

    Narrator(DomainEventFactory events) {
        this.events = events;
    }

    public List<DomainEvent> offerFor(WorldState state, DomainEvent event, StoryAudienceId audience, ScenarioDefinitionRef definition) {
        if (!supports(event.type()) || state.hasNarratorDecisionForSource(event.eventId(), audience)
                || !state.narratorReady(audience)) {
            return List.of();
        }
        boolean audienceAlreadyBusy = state.scenarios().stream().anyMatch(scenario ->
                scenario.audience().equals(audience)
                        && scenario.target().equals(event.subject())
                        && !scenario.status().isTerminal());
        if (audienceAlreadyBusy) return List.of();

        ScenarioInstance scenario = new ScenarioInstance(
                "pm:scenario:" + event.eventId().substring("pm:event:".length()), event.eventId(), event.subject(), audience,
                definition.id(), definition.version(), definition.stages(), definition.requiredCapabilities(),
                definition.encounterProfileId(), definition.encounterProfileVersion(), definition.archetype(), ScenarioStatus.OFFERED, null, "");
        state.putScenario(scenario);
        state.setNarratorCooldown(audience, state.simulationStep() + definition.cooldownSteps());
        DomainEvent offered = events.create(state, DomainEventType.SCENARIO_OFFERED, event.subject(), event.eventId());
        state.addEvent(offered);
        return List.of(offered);
    }

    public List<DomainEvent> noScenario(WorldState state, DomainEvent event, StoryAudienceId audience, String reason) {
        if (!supports(event.type()) || state.hasNarratorDecisionForSource(event.eventId(), audience)) {
            return List.of();
        }
        DomainEvent result = events.create(state, DomainEventType.NO_SCENARIO, event.subject(), audience.value());
        state.addEvent(new DomainEvent(result.eventId(), result.type(), result.subject(), result.simulationStep(),
                audience.value(), event.eventId()));
        return List.of(state.history().getLast());
    }

    public List<ScenarioInstance> offeredFor(WorldState state, StoryAudienceId audience) {
        return state.scenarios().stream().filter(value -> value.audience().equals(audience) && value.status() == ScenarioStatus.OFFERED).toList();
    }

    private static boolean supports(DomainEventType type) {
        return type == DomainEventType.MINE_INFECTED || type == DomainEventType.SETTLEMENT_SUPPLY_DISRUPTED;
    }
}
