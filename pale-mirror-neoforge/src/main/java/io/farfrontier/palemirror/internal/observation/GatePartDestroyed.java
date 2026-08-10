package io.farfrontier.palemirror.internal.observation;

import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;

/** A deduplicated physical fact that one exact PM source-gate part was cleared. */
public record GatePartDestroyed(String id, WorldObjectId facilityId, String slotId, String causationId) implements Observation {
    public GatePartDestroyed {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(causationId, "causationId");
    }
}
