package io.farfrontier.palemirror.domain;

import java.util.Objects;

/**
 * A transient, deterministic Narrator input. It deliberately references an
 * existing domain event instead of becoming a second source of world state.
 */
public record NarrativeCandidate(DomainEvent sourceEvent, StoryAudienceId audience,
                                 NarrativeCandidateType type, ScenarioDefinitionRef definition,
                                 int urgency, int significance, int audienceRelevance,
                                 int capabilityFit, int novelty, int distancePenalty) {
    public NarrativeCandidate {
        Objects.requireNonNull(sourceEvent, "sourceEvent");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(definition, "definition");
        validatePercent(urgency, "urgency");
        validatePercent(significance, "significance");
        validatePercent(audienceRelevance, "audienceRelevance");
        validatePercent(capabilityFit, "capabilityFit");
        validatePercent(novelty, "novelty");
        validatePercent(distancePenalty, "distancePenalty");
    }

    public String id() {
        return sourceEvent.eventId() + ":" + type.name() + ":" + definition.id();
    }

    private static void validatePercent(int value, String name) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " must be 0..100");
    }
}
