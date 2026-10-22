package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Exact physical predecessor retained across asynchronous preparation and recovery. */
public record ProjectionPreimage(String fingerprint, String provenance) {
    public ProjectionPreimage {
        Objects.requireNonNull(fingerprint); Objects.requireNonNull(provenance);
        if (fingerprint.isBlank() || provenance.isBlank()) throw new IllegalArgumentException("empty projection predecessor");
    }
    public boolean matches(String actualFingerprint, String actualProvenance) {
        return fingerprint.equals(actualFingerprint) && provenance.equals(actualProvenance);
    }
}
