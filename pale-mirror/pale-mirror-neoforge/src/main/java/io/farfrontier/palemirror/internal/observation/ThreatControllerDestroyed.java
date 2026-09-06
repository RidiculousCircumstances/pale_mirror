package io.farfrontier.palemirror.internal.observation;

import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;

public record ThreatControllerDestroyed(String id, WorldObjectId facilityId, String causationId) implements Observation {
    public ThreatControllerDestroyed {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(causationId, "causationId");
    }
}
