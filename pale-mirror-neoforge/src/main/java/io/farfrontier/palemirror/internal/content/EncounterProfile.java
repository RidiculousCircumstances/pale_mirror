package io.farfrontier.palemirror.internal.content;

import java.util.List;
import java.util.Objects;

import io.farfrontier.palemirror.domain.ThreatTier;

/** Immutable, datapack-authored presentation request. It cannot contain commands or global Crimson state. */
public record EncounterProfile(String id, int version, List<ActorSlot> actors) {
    public record ActorSlot(String id, String actorProfileId, ThreatTier minimumTier) {
        public ActorSlot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(actorProfileId, "actorProfileId");
            Objects.requireNonNull(minimumTier, "minimumTier");
        }
    }

    public EncounterProfile {
        Objects.requireNonNull(id, "id");
        if (version < 1) throw new IllegalArgumentException("Encounter profile version must be positive");
        actors = List.copyOf(actors);
    }

    public List<ActorSlot> actorsFor(ThreatTier tier) {
        return actors.stream().filter(actor -> tier.atLeast(actor.minimumTier())).toList();
    }
}
