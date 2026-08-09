package io.farfrontier.palemirror.internal.world;

import java.util.Objects;
import java.util.UUID;

/** Stable native reference for one authored encounter slot. */
public record EncounterActorRef(String slotId, String entityTypeId, UUID entityId, Status status) {
    public enum Status { ACTIVE, DEFEATED, MISSING }

    public EncounterActorRef {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(status, "status");
    }
}
