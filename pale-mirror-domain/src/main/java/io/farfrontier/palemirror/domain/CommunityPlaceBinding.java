package io.farfrontier.palemirror.domain;

import java.util.Objects;

public record CommunityPlaceBinding(WorldObjectId communityId, WorldObjectId placeId) {
    public CommunityPlaceBinding {
        Objects.requireNonNull(communityId, "communityId");
        Objects.requireNonNull(placeId, "placeId");
    }
}
