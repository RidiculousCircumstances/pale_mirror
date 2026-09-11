package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Immutable semantic movement instruction for one actor in one bounded operation front. */
public record MovementPlan(SubjectId id, SubjectId actorId, SubjectId tacticalPlanId, long tacticalPlanEpoch,
                           SubjectId frontId, TraversalCapability capability, BlockPosition origin,
                           BlockPosition destination, List<BlockPosition> checkpoints, int currentCheckpoint,
                           LocalNavigationEnvelope envelope) {
    public MovementPlan {
        id = Objects.requireNonNull(id, "movement plan id"); actorId = Objects.requireNonNull(actorId, "movement actor");
        tacticalPlanId = Objects.requireNonNull(tacticalPlanId, "movement tactical plan"); frontId = Objects.requireNonNull(frontId, "movement front");
        capability = Objects.requireNonNull(capability, "movement capability"); origin = Objects.requireNonNull(origin, "movement origin");
        destination = Objects.requireNonNull(destination, "movement destination"); checkpoints = List.copyOf(checkpoints);
        envelope = Objects.requireNonNull(envelope, "movement envelope");
        if (tacticalPlanEpoch < 0L || checkpoints.size() != 2 || currentCheckpoint != 0
                || !checkpoints.getFirst().equals(origin) || !checkpoints.getLast().equals(destination)
                || !envelope.contains(origin) || !envelope.contains(destination)) {
            throw new IllegalArgumentException("movement plan must retain one bounded checkpoint pair");
        }
    }

    public BlockPosition nextCheckpoint() { return checkpoints.get(currentCheckpoint + 1); }
    public boolean permitsObservedSupport(BlockPosition observed) { return envelope.contains(observed); }
}
