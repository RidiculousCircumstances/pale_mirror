package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact target and persisted claim phase of a physical container surface. */
public record ContainerSurface(SubjectId containerId, ContainerLocation location, ContainerSurfaceStatus status) {
    public ContainerSurface { Objects.requireNonNull(containerId, "container id"); Objects.requireNonNull(location, "container location"); Objects.requireNonNull(status, "container surface status"); }
    public ContainerSurface(SubjectId containerId, BlockPosition position, ContainerSurfaceStatus status) {
        this(containerId, new ContainerLocation.Fixed(position), status);
    }
    public boolean fixed() { return location instanceof ContainerLocation.Fixed; }
    /** A block adapter must not mistake a mobile container for a fixed chest. */
    public BlockPosition position() {
        if (location instanceof ContainerLocation.Fixed fixed) return fixed.position();
        throw new IllegalStateException("mobile container requires its declared actor location: " + containerId);
    }
    public BlockPosition position(FrontierWorldState state) {
        if (location instanceof ContainerLocation.Fixed fixed) return fixed.position();
        var actorId = ((ContainerLocation.Mobile) location).actorId();
        var actor = state.actorLocations().get(actorId);
        if (actor == null) throw new IllegalArgumentException("mobile container lost its declared actor: " + actorId);
        return actor.supportingSurface().support().offset(0, 1, 0);
    }
    public ContainerSurface transitionTo(ContainerSurfaceStatus next) {
        Objects.requireNonNull(next, "container surface status");
        if (!status.mayTransitionTo(next)) throw new IllegalArgumentException("invalid container surface transition: " + status + " -> " + next);
        return new ContainerSurface(containerId, location, next);
    }
}
