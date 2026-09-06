package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

import java.util.Objects;

/** Exact loaded-world survivor state observed before a leased actor returns to COLD execution. */
public record SceneMemberPosition(SubjectId actorId, BodyPosition body, FixedScalar health) {
    public SceneMemberPosition {
        Objects.requireNonNull(actorId, "scene actor id");
        Objects.requireNonNull(body, "scene actor body");
        Objects.requireNonNull(health, "scene actor health");
        if (health.compareTo(FixedScalar.ZERO) <= 0) throw new IllegalArgumentException("released survivor must have positive health");
    }

    public SceneMemberPosition(SubjectId actorId, BodyPosition body) { this(actorId, body, ActorCondition.HEALTHY.health()); }
}
