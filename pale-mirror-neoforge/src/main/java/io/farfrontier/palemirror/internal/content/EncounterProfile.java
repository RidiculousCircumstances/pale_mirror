package io.farfrontier.palemirror.internal.content;

import java.util.List;
import java.util.Objects;

/** Immutable, datapack-authored presentation request. It cannot contain commands or global Crimson state. */
public record EncounterProfile(String id, int version, List<ActorSlot> actors) {
    public record ActorSlot(String id, String entityType) {
        public ActorSlot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(entityType, "entityType");
        }
    }

    public EncounterProfile {
        Objects.requireNonNull(id, "id");
        if (version < 1) throw new IllegalArgumentException("Encounter profile version must be positive");
        actors = List.copyOf(actors);
    }
}
