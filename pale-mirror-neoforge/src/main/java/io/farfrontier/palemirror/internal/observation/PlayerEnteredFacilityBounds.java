package io.farfrontier.palemirror.internal.observation;

import java.util.Objects;

import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;

public record PlayerEnteredFacilityBounds(String id, StoryAudienceId audience, WorldObjectId facilityId) implements Observation {
    public PlayerEnteredFacilityBounds {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(facilityId, "facilityId");
    }
}
