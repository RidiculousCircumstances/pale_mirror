package io.farfrontier.palemirror.internal.observation;

import java.util.Objects;
import java.util.UUID;

import io.farfrontier.palemirror.domain.WorldObjectId;

/** Physical fact about a PM-owned Crimson actor; it does not resolve the facility threat. */
public record CrimsonEncounterActorDestroyed(String id, WorldObjectId facilityId, String slotId,
                                             UUID entityId) implements Observation {
    public CrimsonEncounterActorDestroyed {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(entityId, "entityId");
    }
}
