package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Opaque immutable canonical snapshot for the future store port. */
public record CheckpointImage(WorldId worldId, Revision revision, SimInstant instant, byte[] canonicalState) {
    public CheckpointImage {
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(instant, "instant");
        canonicalState = canonicalState.clone();
    }

    @Override
    public byte[] canonicalState() {
        return canonicalState.clone();
    }
}
