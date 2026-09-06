package io.farfrontier.palemirror.domain;

import java.util.Objects;

/**
 * Stable canonical identity for the biological or occult source affecting one
 * PM facility.  It is data owned by the domain, not an adapter or mod id.
 * A facility has exactly one active source; mixed-source overlays require an
 * explicit future source-composition policy instead of accidental merging.
 */
public record InfectionSourceId(String value) {
    public InfectionSourceId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Infection source id must be namespace:path: " + value);
        }
    }
}
