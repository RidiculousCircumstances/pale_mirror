package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable physical evidence that advances one exact container surface lifecycle. */
public record ContainerSurfaceTransition(SubjectId containerId, ContainerSurfaceStatus status) implements FrontierPayload {
    public ContainerSurfaceTransition {
        Objects.requireNonNull(containerId, "container id");
        Objects.requireNonNull(status, "container surface status");
    }
    @Override public String type() { return "frontier.container_surface_transition"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
