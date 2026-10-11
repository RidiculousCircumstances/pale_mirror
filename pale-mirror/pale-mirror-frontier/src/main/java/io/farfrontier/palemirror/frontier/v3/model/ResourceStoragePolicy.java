package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Institution-issued warehouse capability. Identity, access geometry and filters are explicit. */
public record ResourceStoragePolicy(ShipmentEndpoint endpoint, ResourceStorageFilter filter, int priority, ResourceStorageRole role) {
    public ResourceStoragePolicy {
        Objects.requireNonNull(endpoint); Objects.requireNonNull(filter); Objects.requireNonNull(role);
        if (priority < -1000 || priority > 1000) throw new IllegalArgumentException("storage priority exceeds policy bounds");
    }
}
