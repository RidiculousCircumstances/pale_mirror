package io.farfrontier.palemirror.internal.observation;

import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;

public record MaterializationPostconditionObserved(String id, WorldObjectId facilityId, long desiredRevision,
                                                   String causationId) implements Observation {
    public MaterializationPostconditionObserved {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(causationId, "causationId");
    }
}
