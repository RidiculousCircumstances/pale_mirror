package io.farfrontier.palemirror.internal.world;

import java.util.Objects;
import java.util.UUID;

/** Stable native reference plus bounded PM-owned execution state for one authored encounter slot. */
public record EncounterActorRef(String slotId, String actorProfileId, String entityTypeId, UUID entityId, Status status,
                                long nextRuntimeTick, int actionCounter) {
    public enum Status { ACTIVE, DEFEATED, MISSING, REMOVED }

    public EncounterActorRef {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(actorProfileId, "actorProfileId");
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(status, "status");
    }

    public EncounterActorRef(String slotId, String actorProfileId, String entityTypeId, UUID entityId, Status status) {
        this(slotId, actorProfileId, entityTypeId, entityId, status, 0, 0);
    }
}
