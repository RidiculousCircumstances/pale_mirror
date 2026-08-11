package io.farfrontier.palemirror.domain;

import java.util.List;
import java.util.Comparator;
import java.util.Optional;

/** First rule-based storyteller: it turns a global incident into at most one audience scenario. */
public final class Narrator {
    private final DomainEventFactory events;

    Narrator(DomainEventFactory events) {
        this.events = events;
    }

    public List<DomainEvent> offerFor(WorldState state, DomainEvent event, StoryAudienceId audience, ScenarioDefinitionRef definition) {
        if (!supports(event.type())) return List.of();
        return offer(state, event, audience, definition);
    }

    /**
     * Selects one story per audience from simultaneous regional facts. The
     * scoring inputs are deliberately supplied by the runtime: the domain does
     * not inspect positions, adapters, or Minecraft capability providers.
     */
    public List<DomainEvent> offerBest(WorldState state, StoryAudienceId audience,
                                       List<NarrativeCandidate> candidates) {
        if (!state.narratorReady(audience)) return List.of();
        Optional<NarrativeCandidate> selected = candidates.stream()
                .filter(candidate -> candidate.audience().equals(audience))
                .filter(candidate -> !state.hasNarratorDecisionForSource(candidate.sourceEvent().eventId(), audience))
                .filter(candidate -> !audienceBusyForTarget(state, audience, candidate.sourceEvent().subject()))
                .sorted(Comparator.comparingInt((NarrativeCandidate candidate) -> score(state, candidate)).reversed()
                        .thenComparing(NarrativeCandidate::id))
                .findFirst();
        if (selected.isEmpty()) return List.of();
        NarrativeCandidate candidate = selected.get();
        if (score(state, candidate) < 220) {
            return noScenario(state, candidate.sourceEvent(), audience, "candidate score below pacing threshold");
        }
        return offer(state, candidate.sourceEvent(), audience, candidate.definition());
    }

    /** Stable, explainable coarse score; it avoids hidden floating-point heuristics. */
    public int score(WorldState state, NarrativeCandidate candidate) {
        int recentSameArchetype = (int) state.scenarios().stream()
                .filter(scenario -> scenario.audience().equals(candidate.audience()))
                .filter(scenario -> scenario.archetype() == candidate.definition().archetype())
                .filter(scenario -> sourceStep(state, scenario.sourceEventId()) >= state.simulationStep() - 24)
                .count();
        int activeIntensity = (int) state.scenarios().stream()
                .filter(scenario -> scenario.audience().equals(candidate.audience()))
                .filter(scenario -> !scenario.status().isTerminal()).count();
        return candidate.urgency() * 3 + candidate.significance() * 2
                + candidate.audienceRelevance() * 2 + candidate.capabilityFit() * 2
                + candidate.novelty() - candidate.distancePenalty() * 2
                - recentSameArchetype * 45 - activeIntensity * 25;
    }

    private long sourceStep(WorldState state, String eventId) {
        return state.history().stream().filter(event -> event.eventId().equals(eventId))
                .mapToLong(DomainEvent::simulationStep).findFirst().orElse(Long.MIN_VALUE / 2);
    }

    private List<DomainEvent> offer(WorldState state, DomainEvent event, StoryAudienceId audience,
                                    ScenarioDefinitionRef definition) {
        if (state.hasNarratorDecisionForSource(event.eventId(), audience) || !state.narratorReady(audience)
                || audienceBusyForTarget(state, audience, event.subject())) return List.of();
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
        if (state.hasNarratorDecisionForSource(event.eventId(), audience)) {
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
        return type == DomainEventType.MINE_INFECTED || type == DomainEventType.SETTLEMENT_CRISIS_DETECTED;
    }

    private static boolean audienceBusyForTarget(WorldState state, StoryAudienceId audience, WorldObjectId target) {
        return state.scenarios().stream().anyMatch(scenario -> scenario.audience().equals(audience)
                && scenario.target().equals(target) && !scenario.status().isTerminal());
    }
}
