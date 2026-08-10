package io.farfrontier.palemirror.internal.world;

import java.util.Objects;
import java.util.UUID;

/** Stable native reference plus bounded PM-owned execution state for one authored encounter slot. */
public record EncounterActorRef(String slotId, String actorProfileId, String entityTypeId, UUID entityId, Status status,
                                long nextRuntimeTick, int actionCounter, int combatHitPoints,
                                long nextMovementTick, int routeCursor) {
    public static final int UNINITIALIZED_COMBAT_HIT_POINTS = -1;
    public enum Status { ACTIVE, DEFEATED, MISSING, REMOVED }

    public EncounterActorRef {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(actorProfileId, "actorProfileId");
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(status, "status");
    }

    public EncounterActorRef(String slotId, String actorProfileId, String entityTypeId, UUID entityId, Status status) {
        this(slotId, actorProfileId, entityTypeId, entityId, status, 0, 0, UNINITIALIZED_COMBAT_HIT_POINTS, 0, 0);
    }

    public EncounterActorRef(String slotId, String actorProfileId, String entityTypeId, UUID entityId, Status status,
                             long nextRuntimeTick, int actionCounter) {
        this(slotId, actorProfileId, entityTypeId, entityId, status, nextRuntimeTick, actionCounter,
                UNINITIALIZED_COMBAT_HIT_POINTS, 0, 0);
    }

    public EncounterActorRef(String slotId, String actorProfileId, String entityTypeId, UUID entityId, Status status,
                             long nextRuntimeTick, int actionCounter, int combatHitPoints) {
        this(slotId, actorProfileId, entityTypeId, entityId, status, nextRuntimeTick, actionCounter,
                combatHitPoints, 0, 0);
    }
}
