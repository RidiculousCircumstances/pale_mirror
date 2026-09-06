package io.farfrontier.palemirror.domain;

import java.util.Objects;

public record SiteCapability(WorldObjectId siteId, SiteCapabilityType type, ResourceKind resource, int capacity) {
    public SiteCapability {
        Objects.requireNonNull(siteId, "siteId");
        Objects.requireNonNull(type, "type");
        if (capacity < 0) throw new IllegalArgumentException("Site capability must not be negative");
    }
}
