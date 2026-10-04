package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.Objects;
import java.util.Optional;

/** Owner-local approach to an unfinished station, not a second physical position writer. */
public record StationApproachState(long revision, Optional<TraversalRejoin> approach,
                                     Optional<SurfaceAnchor> waitingOrigin) {
    public StationApproachState {
        Objects.requireNonNull(approach); Objects.requireNonNull(waitingOrigin);
        if (revision < 1 || approach.isPresent() && waitingOrigin.isPresent())
            throw new IllegalArgumentException("station continuation requires one versioned spatial state");
    }
    public static StationApproachState initial() {
        return new StationApproachState(1L, Optional.empty(), Optional.empty());
    }
    public boolean pending() { return approach.isPresent() || waitingOrigin.isPresent(); }
    public SurfaceAnchor current(SurfaceAnchor semanticCheckpoint) {
        return approach.map(TraversalRejoin::current).orElseGet(() -> waitingOrigin.orElse(semanticCheckpoint));
    }
    public StationApproachState cleared() {
        return pending() ? new StationApproachState(Math.incrementExact(revision), Optional.empty(), Optional.empty()) : this;
    }
}
