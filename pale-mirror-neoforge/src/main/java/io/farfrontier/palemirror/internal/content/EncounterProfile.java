package io.farfrontier.palemirror.internal.content;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.farfrontier.palemirror.domain.ThreatTier;

/** Immutable, datapack-authored presentation request. It cannot contain commands or global Crimson state. */
public record EncounterProfile(String id, int version, List<ActorSlot> actors, List<Composition> compositions) {
    public record ActorSlot(String id, String actorProfileId, ThreatTier minimumTier) {
        public ActorSlot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(actorProfileId, "actorProfileId");
            Objects.requireNonNull(minimumTier, "minimumTier");
        }
    }

    /**
     * A weighted set of slots for one exact PM tier.  Exact matching is
     * intentional: a late-stage site must never randomly fall back to a
     * foothold composition just because that composition is also eligible.
     */
    public record Composition(String id, ThreatTier tier, int weight, List<ActorSlot> actors) {
        public Composition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(tier, "tier");
            if (weight < 1) throw new IllegalArgumentException("Composition weight must be positive");
            actors = List.copyOf(actors);
            if (actors.isEmpty()) throw new IllegalArgumentException("Composition must contain at least one actor slot");
        }
    }

    public EncounterProfile(String id, int version, List<ActorSlot> actors) {
        this(id, version, actors, List.of());
    }

    public EncounterProfile {
        Objects.requireNonNull(id, "id");
        if (version < 1) throw new IllegalArgumentException("Encounter profile version must be positive");
        actors = List.copyOf(actors);
        compositions = List.copyOf(compositions);
        if (actors.isEmpty() && compositions.isEmpty()) throw new IllegalArgumentException("Encounter profile must contain actors or compositions");
    }

    public List<ActorSlot> actorsFor(ThreatTier tier) {
        return actors.stream().filter(actor -> tier.atLeast(actor.minimumTier())).toList();
    }

    public Composition selectComposition(ThreatTier tier, long worldSeed, String worldObjectId, long desiredRevision) {
        List<Composition> eligible = compositions.stream().filter(value -> tier == value.tier())
                .sorted(Comparator.comparing(Composition::id)).toList();
        if (eligible.isEmpty()) return null;
        int totalWeight = eligible.stream().mapToInt(Composition::weight).sum();
        int selected = Math.floorMod((int) deterministicHash(worldSeed, worldObjectId, desiredRevision, id), totalWeight);
        for (Composition composition : eligible) {
            selected -= composition.weight();
            if (selected < 0) return composition;
        }
        throw new IllegalStateException("Encounter composition selection exhausted weights for " + id);
    }

    public Composition composition(String compositionId) {
        return compositions.stream().filter(value -> value.id().equals(compositionId)).findFirst().orElse(null);
    }

    private static long deterministicHash(long worldSeed, String worldObjectId, long revision, String profileId) {
        long value = worldSeed ^ revision;
        String key = worldObjectId + "|" + profileId;
        for (int index = 0; index < key.length(); index++) {
            value ^= key.charAt(index);
            value *= 0x100000001B3L;
        }
        return value ^ (value >>> 32);
    }
}
