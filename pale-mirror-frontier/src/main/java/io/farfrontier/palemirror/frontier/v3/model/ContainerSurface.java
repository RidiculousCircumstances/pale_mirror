package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact target and persisted claim phase of a physical container surface. */
public record ContainerSurface(SubjectId containerId, BlockPosition position, ContainerSurfaceStatus status) {
    public ContainerSurface { Objects.requireNonNull(containerId, "container id"); Objects.requireNonNull(position, "container position"); Objects.requireNonNull(status, "container surface status"); }
    public ContainerSurface transitionTo(ContainerSurfaceStatus next) {
        Objects.requireNonNull(next, "container surface status");
        if (!status.mayTransitionTo(next)) throw new IllegalArgumentException("invalid container surface transition: " + status + " -> " + next);
        return new ContainerSurface(containerId, position, next);
    }
}
