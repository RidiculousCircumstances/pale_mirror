package io.farfrontier.palemirror.internal.observation;

import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;

/** A deduplicated physical fact that one PM-owned siege gate was cleared. */
public record SiegeGateDestroyed(String id, WorldObjectId facilityId, String slotId, String causationId) implements Observation {
    public SiegeGateDestroyed {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(causationId, "causationId");
    }
}
