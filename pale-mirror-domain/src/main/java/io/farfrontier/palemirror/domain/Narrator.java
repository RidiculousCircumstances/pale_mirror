package io.farfrontier.palemirror.domain;

import java.util.List;

/** First rule-based storyteller: it turns a global incident into at most one audience scenario. */
public final class Narrator {
    private final DomainEventFactory events;

    Narrator(DomainEventFactory events) {
        this.events = events;
    }

    public List<DomainEvent> offerFor(WorldState state, DomainEvent event, StoryAudienceId audience) {
        if (event.type() != DomainEventType.MINE_INFECTED || state.hasScenarioForSource(event.eventId(), audience)) {
            return List.of();
        }
        boolean audienceAlreadyBusy = state.scenarios().stream().anyMatch(scenario ->
                scenario.audience().equals(audience)
                        && scenario.target().equals(event.subject())
                        && !scenario.status().isTerminal());
        if (audienceAlreadyBusy) return List.of();

        ScenarioInstance scenario = new ScenarioInstance(
                "pm:scenario:" + event.eventId().substring("pm:event:".length()), event.eventId(), event.subject(), audience,
                "pale_mirror:investigation_recovery", "1", ScenarioStatus.OFFERED);
        state.putScenario(scenario);
        DomainEvent offered = events.create(state, DomainEventType.SCENARIO_OFFERED, event.subject(), event.eventId());
        state.addEvent(offered);
        return List.of(offered);
    }

    public List<ScenarioInstance> offeredFor(WorldState state, StoryAudienceId audience) {
        return state.scenarios().stream().filter(value -> value.audience().equals(audience) && value.status() == ScenarioStatus.OFFERED).toList();
    }
}
