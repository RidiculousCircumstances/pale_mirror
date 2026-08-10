package io.farfrontier.palemirror.domain;

import java.util.Objects;

public record SiteAffiliation(WorldObjectId siteId, WorldObjectId objectId, SiteAffiliationRole role) {
    public SiteAffiliation {
        Objects.requireNonNull(siteId, "siteId");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(role, "role");
    }
}
