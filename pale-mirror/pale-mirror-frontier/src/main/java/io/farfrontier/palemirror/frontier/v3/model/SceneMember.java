package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.UUID;

/** One canonical actor and its deterministic future Minecraft body identity in a scene lease. */
public record SceneMember(SubjectId actorId, UUID entityId) {
    public SceneMember {
        Objects.requireNonNull(actorId, "scene member actor");
        Objects.requireNonNull(entityId, "scene member entity UUID");
    }
}
